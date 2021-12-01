/**
 *
 */
package org.theseed.reports;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.clusters.Cluster;
import org.theseed.clusters.methods.ClusterMergeMethod;
import org.theseed.counters.CountMap;
import org.theseed.genome.Feature;
import org.theseed.genome.Genome;
import org.theseed.io.TabbedLineReader;
import org.theseed.rna.RnaFeatureData;
import org.theseed.utils.ParseFailureException;

import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import static j2html.TagCreator.*;

/**
 * The analytical cluster report is designed for human viewing, and contains a comprehensive
 * analysis of each cluster.  The cluster heading describes the height, score, size, and other
 * attributes of the cluster.  Then, for each feature, we list the gene name, the functional
 * assignment, the operon ID, the list of modulons, and the list of subsystems.  These last
 * come from a groups file.
 *
 * The report is large and unwieldy, so it is output in the form of HTML.
 *
 * @author Bruce Parrello
 *
 */
public class AnalyticalClusterReporter extends ClusterReporter {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(AnalyticalClusterReporter.class);
    /** map of feature IDs to RNA feature data objects */
    Map<String, RnaFeatureData> featMap;
    /** map of feature IDs to subsystem IDs */
    Map<String, String[]> subMap;
    /** maximum cluster size */
    private int maxClusterSize;
    /** number of nontrivial clusters */
    private int nonTrivial;
    /** number of features in nontrivial clusters */
    private int coverage;
    /** clustering threshold */
    private double threshold;
    /** clustering method */
    private ClusterMergeMethod method;
    /** number of features in operons */
    private int opCount;
    /** number of features in modulons */
    private int modCount;
    /** number of features in regulons */
    private int regCount;
    /** current cluster number */
    private int clNum;
    /** constant for no subsystems */
    private static final String[] NO_SUBS = new String[0];
    /** list of report sections */
    private List<DomContent> sections;
    /** table of contents for clusters */
    private ContainerTag contents;
    /** summary notes list */
    private ContainerTag notesList;
    /** title prefix */
    private String titlePrefix;
    /** HTML tag for styles */
    private static final ContainerTag STYLES = style("td.num, th.num { text-align: right; }\n" +
            "td.flag, th.flag { text-align: center; }\ntd.text, th.text { text-align: left; }\n" +
            "td.big, th.big { width: 20% }\n" +
            "td, th { border-style: groove; padding: 2px; vertical-align: top; min-width: 10px; }\n" +
            "table { border-collapse: collapse; width: 95vw; font-size: small }\n" +
            "body { font-family: Verdana, Arial, Helvetica, sans-serif; font-size: small; }\n" +
            "h1, h2, h3 { font-family: Georgia, \"Times New Roman\", Times, serif; }");
    /** list of table column headers */
    private static final String[] COLUMNS = new String[] { "fid", "gene", "bNum", "regulon",
            "operon", "modulons", "subsystems", "function" };
    /** report title */
    private static final String TITLE = "Clustering Report";

    /**
     * Construct an analytical cluster report.
     *
     * @param processor		controlling command processor
     *
     * @throws IOException
     */
    public AnalyticalClusterReporter(IParms processor) throws IOException, ParseFailureException {
        // Get the clustering specs.
        this.method = processor.getMethod();
        this.threshold = processor.getMinSimilarity();
        // Get the title prefix.  This is a human-readable report.
        this.titlePrefix = processor.getTitlePrefix();
        // We also need the maximum cluster size for the title.
        this.maxClusterSize = processor.getMaxSize();
        // Validate the genome file.
        File genomeFile = processor.getGenomeFile();
        if (genomeFile == null)
            throw new ParseFailureException("Genome file is required for report type ANALYTICAL.");
        // Get all the feature data from the genome.
        this.featMap = new HashMap<String, RnaFeatureData>(4000);
        Genome genome = new Genome(processor.getGenomeFile());
        for (Feature feat : genome.getPegs()) {
            RnaFeatureData featData = new RnaFeatureData(feat);
            this.featMap.put(feat.getId(), featData);
        }
        // Read all the groups from the group file.
        this.subMap = new HashMap<String, String[]>(4000);
        File groupFile = processor.getGroupFile();
        if (groupFile == null)
            throw new ParseFailureException("Group file is required for report type ANALYTICAL.");
        try (TabbedLineReader groupStream = new TabbedLineReader(groupFile)) {
            this.opCount = 0;
            this.modCount = 0;
            this.regCount = 0;
            for (TabbedLineReader.Line line : groupStream) {
                String fid = line.get(0);
                RnaFeatureData featData = this.featMap.get(fid);
                if (featData != null) {
                    int regulon = line.getInt(2);
                    if (regulon > 0) {
                        featData.setAtomicRegulon(regulon);
                        this.regCount++;
                    }
                    String operon = line.get(3);
                    if (! StringUtils.isBlank(operon)) {
                        featData.setOperon(operon);
                        this.opCount++;
                    }
                    String modulons = line.get(1);
                    if (! modulons.isBlank()) {
                        featData.setiModulons(modulons);
                        this.modCount++;
                    }
                    String[] subsystems = StringUtils.split(line.get(4), ',');
                    if (subsystems.length > 0)
                        this.subMap.put(fid, subsystems);
                }
            }
        }
    }

    @Override
    protected void writeHeader() {
        // Set up the counters.
        this.coverage = 0;
        this.nonTrivial = 0;
        this.clNum = 0;
        // Compute the title.
        String title = String.format("Cluster Analysis Report using Method %s with Threshold %1.4f",
                this.method, this.threshold);
        if (this.titlePrefix != null)
            title = this.titlePrefix + " " + title;
        if (this.maxClusterSize < Integer.MAX_VALUE)
            title += String.format(" and Size Limit %d", this.maxClusterSize);
        // Start the report.
        this.sections = new ArrayList<DomContent>(1000);
        this.sections.add(h1(title));
        this.sections.add(h2("Table of Contents"));
        // Initialize the table of contents.
        this.contents = ul().with(li(a("Summary Statistics").withHref("#summary")));
        this.sections.add(this.contents);
        // Initialize the summary notes section.
        this.sections.add(h2(a("Summary Statistics").withName("summary")));
        this.notesList = ul();
        this.sections.add(this.notesList);
        // The table of contents and the summary statistics will be updated later with more
        // text, but they are already placed so as to appear at the top of the output document.
    }

    @Override
    public void writeCluster(Cluster cluster) {
        // Only process a nontrivial cluster.
        int clSize = cluster.size();
        if (clSize > 1) {
            // Count this cluster as nontrivial.
            this.nonTrivial++;
            // Create the cluster name.
            clNum++;
            String clId = String.format("CL%d", clNum);
            // Create the table of contents entry for this cluster.
            ContainerTag linker = li(a(String.format("%s (%s) size %d", clId, cluster.getId(),
                    clSize)).withHref("#" + clId));
            this.contents.with(linker);
            // Here we track the groups represented in this cluster.  For each group we need the number of
            // cluster members in the group.
            var modCounters = new CountMap<String>();
            var opCounters = new CountMap<String>();
            var subCounters = new CountMap<String>();
            var regCounters = new CountMap<String>();
            // Set up the table.
            ContainerTag tableHead = tr().with(Arrays.stream(COLUMNS).map(x -> th(x).withStyle("text")));
            ContainerTag table = table().with(tableHead);
            // Write the cluster members.
            for (String fid : cluster.getMembers()) {
                RnaFeatureData feat = this.featMap.get(fid);
                String[] subsystems = this.subMap.getOrDefault(fid, NO_SUBS);
                String[] modulons = feat.getiModulons();
                String operon = feat.getOperon();
                int arNum = feat.getAtomicRegulon();
                String arString = (arNum > 0 ? String.format("AR%d", arNum) : "");
                String[] columns = new String[] { fid, feat.getGene(), feat.getBNumber(),
                        arString, operon, groupString(modulons), groupString(subsystems),
                        feat.getFunction() };
                List<ContainerTag> row = Arrays.stream(columns).map(x -> td(x).withClass("text"))
                        .collect(Collectors.toList());
                row.get(row.size() - 2).withClass("big");
                row.get(row.size() - 1).withClass("big");
                table.with(tr().with(row));
                // Count this feature in each of its groups.
                if (! StringUtils.isEmpty(operon))
                    opCounters.count(operon);
                if (arNum > 0)
                    regCounters.count(arString);
                Arrays.stream(subsystems).forEach(x -> subCounters.count(x));
                Arrays.stream(modulons).forEach(x -> modCounters.count(x));
                // Count this feature as covered.
                this.coverage++;
            }
            // Create the section heading.
            ContainerTag header = h2(a(String.format("%s: size %d, height %d, score %1.4f",
                    clId, clSize, cluster.getHeight(), cluster.getScore()))
                    .withName(clId));
            // Now we do the evidence computation.  The evidence is the number of pairs in a group.
            // We compute this for each type of group.
            int pairSize = clSize * (clSize - 1)/2;
            ContainerTag evidence = ul().with(li(String.format("%d possible pairs.", pairSize)));
            this.addEvidence(evidence, "modulon", "modulons", modCounters);
            this.addEvidence(evidence, "operon", "operons", opCounters);
            this.addEvidence(evidence, "regulon", "regulons", regCounters);
            this.addEvidence(evidence, "subsystem", "subsystems", subCounters);
            // Add the header, the evidence, and the table as another section.
            DomContent section = div().with(header, evidence, table);
            this.sections.add(section);
        }
    }

    /**
     * This method adds an evidence indication to the evidence bullet list based on a particular type of grouping.
     *
     * @param evidence		evidence bullet list
     * @param typeString	name of the grouping
     * @param pluralString	plural name of the grouping
     * @param counters		count map showing the number of items in each group
     */
    private void addEvidence(ContainerTag evidence, String typeString, String pluralString, CountMap<String> counters) {
        // We sum the triangle number of each group size count.  This is the total number of pairs in groups
        // of this type.
        int pairCount = 0;
        int groupCount = 0;
        List<CountMap<String>.Count> counts = counters.sortedCounts();
        if (counts.size() > 0) {
            for (CountMap<String>.Count count : counts) {
                int n = count.getCount();
                if (n > 1) {
                    // Here we have a meaningful group.
                    groupCount++;
                    pairCount += n*(n-1)/2;
                }
            }
            // If there is at least one pair, make it a bullet point.
            CountMap<String>.Count bigCount = counts.get(0);
            if (pairCount == 1) {
                // Here we have a single pair.
                evidence.with(li(String.format("One pair in %s %s.", typeString, bigCount.getKey())));
            } else if (pairCount > 1) {
                // Here the pair count is non-trivial.
                evidence.with(li(String.format("%d pairs found in %d %s. Largest %s is %s with %d features.",
                        pairCount, groupCount, pluralString, typeString, bigCount.getKey(), bigCount.getCount())));
            }
        }
    }

    /**
     * @return a list of the groups, either comma-delimited or as an empty string
     *
     * @param groups	array of group names
     */
    private static String groupString(String[] groups) {
        String retVal;
        switch (groups.length) {
        case 0:
            retVal = "";
            break;
        case 1:
            retVal = groups[0];
            break;
        default:
            retVal = StringUtils.join(groups, ", ");
            break;
        }
        return retVal;
    }

    @Override
    public void closeReport() {
        // Write the statistics.
        this.notesList.with(li(String.format("%d nontrivial clusters covering %d features.",
                        this.nonTrivial, this.coverage)));
        // Count the subsystems.
        Set<String> groups = this.subMap.values().stream().flatMap(x -> Arrays.stream(x))
                .collect(Collectors.toSet());
        this.notesList.with(li(String.format("%d features in %d subsystems.",
                this.subMap.size(), groups.size())));
        // Count the operons.
        groups = this.featMap.values().stream().map(x -> x.getOperon())
                .filter(x -> ! StringUtils.isEmpty(x)).collect(Collectors.toSet());
        this.notesList.with(li(String.format("%d features in %d operons.",
                this.opCount, groups.size())));
        // Count the regulons.
        OptionalInt regulonMax = this.featMap.values().stream().mapToInt(x -> x.getAtomicRegulon()).max();
        int regulonCount = (regulonMax.isEmpty() ? 0 : regulonMax.getAsInt() + 1);
        this.notesList.with(li(String.format("%d features in %d regulons.", this.regCount, regulonCount)));
        // Finally, count the modulons.
        groups = this.featMap.values().stream().flatMap(x -> Arrays.stream(x.getiModulons()))
                .collect(Collectors.toSet());
        this.notesList.with(li(String.format("%d features in %d modulons.",
                this.modCount, groups.size())));
        // Assemble the page.
        ContainerTag body = body().with(this.sections);
        ContainerTag page = html(head(title(TITLE), STYLES), body);
        this.println(page.render());
    }

}
