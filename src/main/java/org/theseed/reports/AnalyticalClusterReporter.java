/**
 *
 */
package org.theseed.reports;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.theseed.clusters.Cluster;
import org.theseed.clusters.ClusterGroup;
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
public class AnalyticalClusterReporter extends HtmlClusterReporter {

    // FIELDS
    /** map of feature IDs to RNA feature data objects */
    Map<String, RnaFeatureData> featMap;
    /** map of feature IDs to subsystem IDs */
    Map<String, String[]> subMap;
    /** number of features in operons */
    private int opCount;
    /** number of features in modulons */
    private int modCount;
    /** number of features in regulons */
    private int regCount;
    /** constant for no subsystems */
    private static final String[] NO_SUBS = new String[0];
    /** list of table column headers */
    private static final String[] COLUMNS = new String[] { "fid", "gene", "bNum", "regulon",
            "operon", "modulons", "subsystems", "function" };

    /**
     * Construct an analytical cluster report.
     *
     * @param processor		controlling command processor
     *
     * @throws IOException
     */
    public AnalyticalClusterReporter(IParms processor) throws IOException, ParseFailureException {
        super(processor);
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
    protected DomContent formatClusterTable(Cluster cluster) {
        // Here we track the groups represented in this cluster.  For each group we need the number of
        // cluster members in the group.
        var modCounters = new CountMap<String>();
        var opCounters = new CountMap<String>();
        var subCounters = new CountMap<String>();
        var regCounters = new CountMap<String>();
        // Set up the table.
        ContainerTag tableHead = tr().with(Arrays.stream(COLUMNS).map(x -> th(x).withClass("text")));
        ContainerTag retVal = table().with(tableHead);
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
            retVal.with(tr().with(row));
            // Count this feature in each of its groups.
            if (! StringUtils.isEmpty(operon))
                opCounters.count(operon);
            if (arNum > 0)
                regCounters.count(arString);
            Arrays.stream(subsystems).forEach(x -> subCounters.count(x));
            Arrays.stream(modulons).forEach(x -> modCounters.count(x));
            // Count this feature as covered.
        }
        // Now we do the evidence computation.  The evidence is the number of pairs in a group.
        // We compute this for each type of group.
        this.addEvidence("modulon", "modulons", modCounters);
        this.addEvidence("operon", "operons", opCounters);
        this.addEvidence("regulon", "regulons", regCounters);
        this.addEvidence("subsystem", "subsystems", subCounters);
        // Return the table.
        return retVal;
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
    public void addNotes() {
        // Count the subsystems.
        Set<String> groups = this.subMap.values().stream().flatMap(x -> Arrays.stream(x))
                .collect(Collectors.toSet());
        this.addNote(String.format("%d features in %d subsystems.",
                this.subMap.size(), groups.size()));
        // Count the operons.
        groups = this.featMap.values().stream().map(x -> x.getOperon())
                .filter(x -> ! StringUtils.isEmpty(x)).collect(Collectors.toSet());
        this.addNote(String.format("%d features in %d operons.",
                this.opCount, groups.size()));
        // Count the regulons.
        OptionalInt regulonMax = this.featMap.values().stream().mapToInt(x -> x.getAtomicRegulon()).max();
        int regulonCount = (regulonMax.isEmpty() ? 0 : regulonMax.getAsInt() + 1);
        this.addNote(String.format("%d features in %d regulons.", this.regCount, regulonCount));
        // Finally, count the modulons.
        groups = this.featMap.values().stream().flatMap(x -> Arrays.stream(x.getiModulons()))
                .collect(Collectors.toSet());
        this.addNote(String.format("%d features in %d modulons.",
                this.modCount, groups.size()));
    }

    @Override
    public void scanGroup(ClusterGroup mainGroup) {
    }

}

