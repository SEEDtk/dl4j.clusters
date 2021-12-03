/**
 *
 */
package org.theseed.reports;

import static j2html.TagCreator.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.theseed.clusters.Cluster;
import org.theseed.clusters.methods.ClusterMergeMethod;
import org.theseed.counters.CountMap;
import org.theseed.utils.ParseFailureException;

import j2html.tags.ContainerTag;
import j2html.tags.DomContent;

/**
 * This is the base class for cluster reports written to HTML pages.  Such pages have several things in common,
 * including a table of contents and a summary section.  Each cluster is in a section by itself, and is described
 * by some evidence bullet points and a table of members.
 *
 * @author Bruce Parrello
 *
 */
public abstract class HtmlClusterReporter extends ClusterReporter {

    // FIELDS
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
    /** current cluster number */
    private int clNum;
    /** list of report sections */
    private List<DomContent> sections;
    /** table of contents for clusters */
    private ContainerTag contents;
    /** summary notes list */
    private ContainerTag notesList;
    /** title prefix */
    private String titlePrefix;
    /** current evidence list */
    private ContainerTag evidence;
    /** HTML tag for styles */
    private static final ContainerTag STYLES = style("td.num, th.num { text-align: right; }\n" +
            "td.flag, th.flag { text-align: center; }\ntd.text, th.text { text-align: left; }\n" +
            "td.big, th.big { width: 20% }\n" +
            "td, th { border-style: groove; padding: 2px; vertical-align: top; min-width: 10px; }\n" +
            "table { border-collapse: collapse; width: 95vw; font-size: small }\n" +
            "body { font-family: Verdana, Arial, Helvetica, sans-serif; font-size: small; }\n" +
            "h1, h2, h3 { font-family: Georgia, \"Times New Roman\", Times, serif; }");
    /** report title */
    private static final String TITLE = "Clustering Report";

    /**
     * Construct an analytical cluster report.
     *
     * @param processor		controlling command processor
     *
     * @throws IOException
     */
    public HtmlClusterReporter(IParms processor) throws IOException, ParseFailureException {
        // Get the clustering specs.
        this.method = processor.getMethod();
        this.threshold = processor.getMinSimilarity();
        // Get the title prefix.  This is a human-readable report.
        this.titlePrefix = processor.getTitlePrefix();
        // We also need the maximum cluster size for the title.
        this.maxClusterSize = processor.getMaxSize();
    }

    @Override
    protected final void writeHeader() {
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
    public final void writeCluster(Cluster cluster) {
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
            // Update the coverage counter.
            this.coverage += clSize;
            // Create the evidence list.
            int pairSize = clSize * (clSize - 1)/2;
            this.evidence = ul().with(li(String.format("%d possible pairs.", pairSize)));
            // Create the section heading.
            ContainerTag header = h2(a(String.format("%s: size %d, height %d, score %1.4f",
                    clId, clSize, cluster.getHeight(), cluster.getScore()))
                    .withName(clId));
            DomContent table = this.formatClusterTable(cluster);
            // Write out the full section.
            DomContent section = div().with(header, this.evidence, table);
            this.sections.add(section);
        }
    }

    /**
     * @return the HTML table describing this cluster
     *
     * @param cluster	cluster to describe
     */
    protected abstract DomContent formatClusterTable(Cluster cluster);

    /**
     * This method adds an evidence indication to the evidence bullet list based on a particular type of grouping.
     *
     * @param typeString	name of the grouping
     * @param pluralString	plural name of the grouping
     * @param counters		count map showing the number of items in each group
     */
    protected void addEvidence(String typeString, String pluralString, CountMap<String> counters) {
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
                this.evidence.with(li(String.format("One pair in %s %s.", typeString, bigCount.getKey())));
            } else if (pairCount > 1) {
                // Here the pair count is non-trivial.
                this.evidence.with(li(String.format("%d pairs found in %d %s. Largest %s is %s with %d members.",
                        pairCount, groupCount, pluralString, typeString, bigCount.getKey(), bigCount.getCount())));
            }
        }
    }

    /**
     * Add an evidence note that is a simple string.
     *
     * @param note		evidence note to add
     */
    protected void addEvidence(String note) {
        this.evidence.with(li(note));
    }

    /**
     * Add a note to the notes list.
     *
     * @param string	text of note to add
     */
    protected void addNote(String string) {
        this.notesList.with(li(string));
    }

    @Override
    public final void closeReport() {
        // Write the coverage statistic.
        this.addNote(String.format("%d nontrivial clusters covering %d features.",
                        this.nonTrivial, this.coverage));
        // Let the subclass add summary information.
        this.addNotes();
        // Render the page.
        ContainerTag body = body().with(this.sections);
        ContainerTag page = html(head(title(TITLE), STYLES), body);
        this.println(page.render());
    }

    /**
     * Create a cluster table given a list of column names.
     *
     * @param columns	array of column names
     */
    protected ContainerTag startTable(String[] columns) {
        ContainerTag tableHead = tr().with(Arrays.stream(columns).map(x -> th(x).withStyle("text")));
        ContainerTag retVal = table().with(tableHead);
        return retVal;
    }

    /**
     * Add notes to the summary section.
     */
    protected abstract void addNotes();

}
