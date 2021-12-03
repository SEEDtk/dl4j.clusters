/**
 *
 */
package org.theseed.reports;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.theseed.clusters.Cluster;
import org.theseed.clusters.ClusterGroup;
import org.theseed.counters.CountMap;
import org.theseed.ncbi.NcbiConnection;
import org.theseed.ncbi.NcbiListQuery;
import org.theseed.ncbi.NcbiTable;
import org.theseed.ncbi.XmlException;
import org.theseed.ncbi.XmlUtils;
import org.theseed.utils.ParseFailureException;
import org.w3c.dom.Element;

import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import static j2html.TagCreator.*;

/**
 * This reporter generates an HTML report about NCBI samples.  We connect to NCBI directly to get
 * data about each sample for display.
 *
 * @author Bruce Parrello
 *
 */
public class SampleClusterReporter extends HtmlClusterReporter {

    // FIELDS
    /** map of sample IDs to NCBI data records */
    private Map<String, SampleInfo> sampleMap;
    /** batch size for NCBI queries */
    private int batchSize;
    /** NCBI data connection */
    private NcbiConnection ncbi;
    /** projects found */
    private CountMap<String> projectCounts;
    /** pubmed articles found */
    private CountMap<String> pubmedCounts;
    /** URL format for pubmed IDs */
    private static String PUBMED_URL = "https://pubmed.ncbi/nlm.nih.gov/%s/";
    /** URL format for project IDs */
    private static String PROJ_URL = "https://trace.ncbi.nlm.nih.gov/Traces/sra/?study=%s";
    /** cluster table column headers */
    private static String[] COLUMNS = new String[] {"sample_id", "experiment", "project", "pubmed", "title"};
    /** HTML for an empty cell's content */
    private static DomContent EMPTY_CELL = td(rawHtml("&nbsp;")).withClass("text");

    /**
     * This is a tiny utility object that contains the pubmed ID and project ID for each sample.
     */
    protected static class SampleInfo {

        protected DomContent title;
        protected String projectId;
        protected String pubmed;
        protected DomContent expLink;

        /**
         * Create the sample information object from an NCBI record.
         *
         * @param ncbiDatum		sample record returned from NCBI
         */
        protected SampleInfo(Element ncbiDatum) {
            this.pubmed = null;
            this.projectId = null;
            this.expLink = rawHtml("&nbsp;");
            this.title = em("invalid");
            // Compute the experiment title.
            Element experiment = XmlUtils.findFirstByTagName(ncbiDatum, "EXPERIMENT");
            if (experiment != null) {
                String accession = experiment.getAttribute("accession");
                if (! accession.isEmpty()) {
                    String expLink = "https://ncbi.nlm.nih.gov/sra/?term=" + accession;
                    String foundTitle = XmlUtils.getXmlString(experiment, "TITLE");
                    if (foundTitle.isEmpty())
                        foundTitle = accession;
                    this.title = a(foundTitle).withHref(expLink).withTarget("_blank");
                    // Use the experiment page itself as the default experiment link.
                    // This may be overridden by an explicit link.
                    this.expLink = a(accession).withHref(expLink).withTarget("_blank");
                }
            }
            // Find the study.
            Element study = XmlUtils.findFirstByTagName(ncbiDatum, "STUDY");
            // Get the project ID.
            String projAccession = XmlUtils.getXmlString(study, "PRIMARY_ID");
            if (! projAccession.isEmpty())
                this.projectId = projAccession;
            // Get the pubmed ID from the pubmed DB record under STUDY_LINKS.
            this.pubmed = null;
            Element studyLinks = XmlUtils.findFirstByTagName(study, "STUDY_LINKS");
            if (studyLinks != null) {
                Iterator<Element> studyIter = XmlUtils.childrenOf(studyLinks).iterator();
                while (this.pubmed == null && studyIter.hasNext()) {
                    Element child = studyIter.next();
                    String dbType = XmlUtils.getXmlString(child, "DB");
                    if (dbType.contentEquals("pubmed"))
                        this.pubmed = XmlUtils.getXmlString(child, "ID");
                }
            }
            // Get the experiment link.
            Element expLinks = XmlUtils.findFirstByTagName(ncbiDatum, "EXPERIMENT_LINKS");
            if (expLinks != null) {
                Element expUrlLink = XmlUtils.findFirstByTagName(expLinks, "URL_LINK");
                if (expUrlLink != null) {
                    String linkText = XmlUtils.getXmlString(expUrlLink, "LABEL");
                    String linkUrl = XmlUtils.getXmlString(expUrlLink, "URL");
                    this.expLink = a(linkText).withHref(linkUrl).withTarget("_blank");
                }
            }
        }
    }

    /**
     * Construct the cluster reporter for samples.
     *
     * @param processor		controlling command processor
     *
     * @throws ParseFailureException
     * @throws IOException
     */
    public SampleClusterReporter(IParms processor) throws IOException, ParseFailureException {
        super(processor);
        this.batchSize = processor.getBatchSize();
        this.ncbi = new NcbiConnection();
        this.projectCounts = new CountMap<String>();
        this.pubmedCounts = new CountMap<String>();
        this.sampleMap = new HashMap<String, SampleInfo>(1000);
    }

    @Override
    public void scanGroup(ClusterGroup mainGroup) throws IOException {
        log.info("Scanning cluster group for sample data from NCBI.");
        // Get the list of samples.
        List<String> samples = mainGroup.getDataPoints();
        // Create an NCBI list query.  We will run this in batches.
        NcbiListQuery q = new NcbiListQuery(NcbiTable.SRA, "ACCN");
        for (String sample : samples) {
            if (q.size() >= this.batchSize)
                this.runQuery(q);
            q.addId(sample);
        }
        this.runQuery(q);
    }

    /**
     * This method runs a query batch through NCBI to get data on the specified list of samples.
     *
     * @param q		query to run.
     * @throws IOException
     * @throws XmlException
     */
    private void runQuery(NcbiListQuery q) throws IOException {
        try {
            log.info("Retreiving batch of {} samples from from NCBI.", q.size());
            List<Element> sampleItems = q.run(this.ncbi);
            // Loop through the samples found.
            int count = 0;
            for (Element sampleItem : sampleItems) {
                // Get the sample information object.
                SampleInfo info = new SampleInfo(sampleItem);
                // Get the list of sample IDs for this experiment.  (There is almost always only 1.)
                Element runs = XmlUtils.findFirstByTagName(sampleItem, "RUN_SET");
                for (Element run : XmlUtils.childrenOf(runs)) {
                    String sampleId = run.getAttribute("accession");
                    if (! StringUtils.isEmpty(sampleId)) {
                        this.sampleMap.put(sampleId, info);
                        count++;
                    }
                }
            }
            log.info("{} samples found in batch.", count);
        } catch (XmlException e) {
            // Convert the XmlException to an IOException.
            throw new IOException("XML Error: " + e.getMessage());
        }
    }

    @Override
    protected DomContent formatClusterTable(Cluster cluster) {
        // Set up the evidence count tables.
        CountMap<String> projCounts = new CountMap<String>();
        CountMap<String> paperCounts = new CountMap<String>();
        int badSamples = 0;
        // Start the table.
        ContainerTag retVal = this.startTable(COLUMNS);
        // Loop through the cluster members.
        for (String member : cluster.getMembers()) {
            // Start this table row.
            ContainerTag row = tr().with(td(member).withClass("text"));
            SampleInfo info = this.sampleMap.get(member);
            if (info == null) {
                // Here the sample is no longer in pubmed.
                badSamples++;
                row.with(td(em("not found")).withClass("text"))
                    .with(EMPTY_CELL, EMPTY_CELL, EMPTY_CELL);
            } else {
                // Store the experiment ID.
                row.with(td(info.expLink).withClass("text"));
                // Process the project ID.
                this.addGroupCell(row, info.projectId, PROJ_URL, projCounts);
                // Process the pubmed link.
                this.addGroupCell(row, info.pubmed, PUBMED_URL, paperCounts);
                // Add the title.
                row.with(td(info.title).withClasses("text", "big"));
            }
            // Add the row to the table.
            retVal.with(row);
        }
        // If there were bad samples, make a note.
        if (badSamples > 0)
            this.addEvidence(String.format("%d samples are no longer in NCBI.", badSamples));
        // Update the master counters and store the evidence.
        this.projectCounts.accumulate(projCounts);
        this.addEvidence("project", "projects", projCounts);
        this.pubmedCounts.accumulate(paperCounts);
        this.addEvidence("pubmed paper", "pubmed papers", paperCounts);
        // All done.  Output the table.
        return retVal;
    }

    /**
     * Add a column to the specifed table row for a specified group type.
     *
     * @param row			table row being updated
     * @param groupId		group ID for this cell
     * @param linker		format for the group links
     * @param counts		count map for the group type
     */
    private void addGroupCell(ContainerTag row, String groupId, String linker, CountMap<String> counts) {
        if (groupId == null) {
            // Here there is no group of this type.
            row.with(EMPTY_CELL);
        } else {
            // Format the group link.
            DomContent link = a(groupId).withHref(String.format(linker, groupId)).withTarget("_blank");
            row.with(td(link).withClass("text"));
            // Update the counter.
            counts.count(groupId);
        }
    }

    @Override
    protected void addNotes() {
        this.addCountNote(this.projectCounts, "projects");
        this.addCountNote(this.pubmedCounts, "pubmed papers");
    }

    /**
     * Add a note for the specified counted group.
     *
     * @param groupCounts	count map for the group
     * @param groupName		name of the group (plural)
     */
    private void addCountNote(CountMap<String> groupCounts, String groupName) {
        List<CountMap<String>.Count> counts = groupCounts.sortedCounts();
        if (counts.size() >= 1) {
            String biggestProject = counts.get(0).getKey();
            int allCounts = groupCounts.sum();
            this.addNote(String.format("%d samples found in %d %s.  Biggest was %s.", allCounts, counts.size(),
                    groupName, biggestProject));
        }
    }

}
