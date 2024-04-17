/**
 *
 */
package org.theseed.dl4j.clusters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.basic.BaseReportProcessor;
import org.theseed.basic.ParseFailureException;
import org.theseed.clusters.Cluster;
import org.theseed.clusters.ClusterGroup;
import org.theseed.clusters.methods.ClusterMergeMethod;
import org.theseed.reports.ClusterReporter;

/**
 * This commands attempts to cluster objects based on similarity scores.  The input file should
 * contain the two object IDs and a score column.  Objects with high scores will be clustered
 * together.
 *
 * A modified agglomeration-clustering algorithm is used.  The method for measuring similarities
 * between clusters is specified as a tuning parameter, and the merging is stopped when the
 * similarity of the closest clusters drops below a specified score.
 *
 * The clustering report is written to the standard output.
 *
 * The positional parameters are the similarity threshold to use as a minimum cutoff and the
 * name of the input file containing the similarities.
 *
 * The command-line options are as follows:
 *
 * -h	display command-line usage
 * -v	display more frequent log messages
 * -o	output file for the cluster report (if not STDOUT)
 *
 * --col1		index (1-based) or name of column containing the first data point ID (default "1")
 * --col2		index (1-based) or name of column containing the second data point ID (default "2")
 * --score		index (1-based) or name of column containing the similarity score (default "3")
 * --method		method for merging similarities (default COMPLETE)
 * --points		estimated number of data points (default computed from file size)
 * --format		type of report to write (default INDENT)
 * --gto		GTO file for the reference genome for genome-based reports
 * --subFile	output file for subsystem ID mapping produced from the GENOME report
 * --sparse		if specified, the incoming distances are not complete (suppresses a warning)
 * --groups		group file for ANALYTICAL reports
 * --comment	title prefix for ANALYTICAL report
 * --maxSize	maximum permissible cluster size; the default allows unlimited clustering
 *
 * @author Bruce Parrello
 *
 */
public class ClusterProcessor extends BaseReportProcessor implements ClusterReporter.IParms {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(ClusterProcessor.class);
    /** cluster group being built */
    private ClusterGroup mainGroup;
    /** cluster reporting object */
    private ClusterReporter reporter;

    // COMMAND-LINE OPTIONS

    /** input column containing first data point ID */
    @Option(name = "--col1", aliases = { "--c1" }, metaVar = "id1", usage = "index (1-based) or name of column containing the first data point ID")
    private String col1Name;

    /** input column containing second data point ID */
    @Option(name = "--col2", aliases = { "--c2" }, metaVar = "id2", usage = "index (1-based) or name of column containing the second data point ID")
    private String col2Name;

    /** input column containing similarity score */
    @Option(name = "--score", metaVar = "sim", usage = "index (1-based) or name of column containing the similarity score")
    private String scoreName;

    /** method for computing group similarities */
    @Option(name = "--method", usage = "method to use for computing similarity between clusters")
    private ClusterMergeMethod method;

    /** estimated number of input data points */
    @Option(name = "--points", metaVar = "4000", usage = "estimated number of input data points (0 = compute from file size)")
    private int points;

    /** reference genome file for GENOME reports */
    @Option(name = "--gto", metaVar = "83333.1.gto", usage = "reference genome for a GENOME-type report")
    private File genomeFile;

    /** output format */
    @Option(name = "--format", usage = "output report type")
    private ClusterReporter.Type reportType;

    /** suppress warning for incomplete pairings */
    @Option(name = "--sparse", usage = "if specified, the pairings are presumed to be incomplete")
    private boolean sparseMode;

    /** subsystem ID mapping output file */
    @Option(name = "--subFile", usage = "if specified, an output file for subsystem ID mappings in the GENOME report")
    private File subFile;

    /** groups.tbl file for ANALYTICAL report */
    @Option(name = "--groups", metaVar = "groups.tbl", usage = "groups definition file for FEATURES report")
    private File groupFile;

    /** title prefix */
    @Option(name = "--comment", metaVar = "Operon-Weighted", usage = "prefix to add to titles on human-readable reports")
    private String titlePrefix;

    /** maximum cluster size */
    @Option(name = "--maxSize", metaVar = "10", usage = "maximum permissible cluster size")
    private int maxSize;

    /** batch size for web queries */
    @Option(name = "--batchSize", aliases = { "-b", "--batch" }, metaVar = "50", usage = "batch size for web queries")
    private int batchSize;

    /** minimum similarity for joining */
    @Argument(index = 0, metaVar = "minScore", usage = "minimum acceptable similarity score for clustering", required = true)
    private double minScore;

    /** name of input file */
    @Argument(index = 1, metaVar = "inFile", usage = "name of the input file containing the similarity scores for all pairs", required = true)
    private File inFile;

    @Override
    protected void setReporterDefaults() {
        this.col1Name = "1";
        this.col2Name = "2";
        this.scoreName = "3";
        this.method = ClusterMergeMethod.COMPLETE;
        this.points = 0;
        this.reportType = ClusterReporter.Type.TABULAR;
        this.genomeFile = null;
        this.sparseMode = false;
        this.groupFile = null;
        this.titlePrefix = null;
        this.maxSize = Integer.MAX_VALUE;
        this.batchSize = 100;
    }

    @Override
    protected void validateReporterParms() throws IOException, ParseFailureException {
        // Validate the size limit.
        if (this.maxSize < 2)
            throw new ParseFailureException("Maximum cluster size must be at least 2.");
        // Validate the batch size.
        if (this.batchSize < 1)
            throw new ParseFailureException("Batch size must be at least 1.");
        // Validate the input file.
        if (! this.inFile.canRead())
            throw new FileNotFoundException("Input file " + this.inFile + " is not found or unreadable.");
        // Estimate the number of data points.
        if (this.points <= 0) {
            this.points = ClusterGroup.estimateDataPoints(this.inFile);
            log.info("{} estimated data points in {}.", this.points, this.inFile);
        } else
            log.info("Expecting {} data points in {}.", this.points, this.inFile);
        log.info("Minimum merge score is {}.", this.minScore);
        // Create the cluster group and load it.
        this.mainGroup = new ClusterGroup(this.points, this.method);
        this.mainGroup.load(this.inFile, col1Name, col2Name, scoreName, this.sparseMode);
        if (this.mainGroup.size() < 2)
            throw new ParseFailureException("Too few datapoints in input file for clustering.");
        // Store the size limit.
        this.mainGroup.setMaxSize(this.maxSize);
        if (this.maxSize == Integer.MAX_VALUE)
            log.info("Unlimited cluster size allowed.");
        else
            log.info("Maximum cluster size is {}.", this.maxSize);
        // Create the output report.
        this.reporter = this.reportType.create(this);
    }

    @Override
    protected void runReporter(PrintWriter writer) throws Exception {
        // Perform the merges.
        int mergeCount = 0;
        while(this.mainGroup.merge(this.minScore)) {
            mergeCount++;
            if (mergeCount % 100 == 0)
                log.info("{} merges performed.", mergeCount);
        }
        // Get the cluster list.
        List<Cluster> clusters = this.mainGroup.getClusters();
        log.info("{} merges resulting in {} clusters.", mergeCount, clusters.size());
        // Get some useful statistics.
        Cluster largest = clusters.get(0);
        int nonTrivial = 0;
        int clustered = 0;
        for (Cluster cluster : clusters) {
            if (cluster.size() > 1) {
                nonTrivial++;
                clustered += cluster.size();
            }
        }
        log.info("Largest cluster size is {} with height {}, {} nontrivial clusters found containing {} data points.",
                largest.size(), largest.getHeight(), nonTrivial, clustered);
        // Now write the report.
        this.reporter.openReport(writer);
        this.reporter.scanGroup(this.mainGroup);
        for (Cluster cluster : clusters)
            this.reporter.writeCluster(cluster);
        this.reporter.closeReport();
    }

    @Override
    public File getGenomeFile() {
        return this.genomeFile;
    }

    @Override
    public File getGroupFile() {
        return this.groupFile;
    }

    @Override
    public ClusterMergeMethod getMethod() {
        return this.method;
    }

    @Override
    public double getMinSimilarity() {
        return this.minScore;
    }

    @Override
    public String getTitlePrefix() {
        return this.titlePrefix;
    }

    @Override
    public int getMaxSize() {
        return this.maxSize;
    }

    @Override
    public int getBatchSize() {
        return this.batchSize;
    }

    @Override
    public File getSubFile() {
        return this.subFile;
    }

}
