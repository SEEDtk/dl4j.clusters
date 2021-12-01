/**
 *
 */
package org.theseed.reports;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.theseed.clusters.Cluster;
import org.theseed.genome.Feature;
import org.theseed.genome.Genome;
import org.theseed.utils.ParseFailureException;

/**
 * This is a special report for clusters of features in a genome.  The genome GTO
 * is specified as a parameter for the command processor, and when each cluster is
 * written, the gene name and the feature role are included.
 *
 * @author Bruce Parrello
 *
 */
public class GenomeClusterReporter extends ClusterReporter {

    // FIELDS
    /** file containing the reference genome */
    private File genomeFile;
    /** current cluster number */
    private int num;
    /** reference genome */
    private Genome genome;

    /**
     * Construct a genome clustering report.
     *
     * @param processor		controlling command processor
     *
     * @throws IOException
     * @throws ParseFailureException
     */
    public GenomeClusterReporter(IParms processor) throws IOException, ParseFailureException {
        this.genomeFile = processor.getGenomeFile();
        if (this.genomeFile == null)
            throw new ParseFailureException("Reference genome file is required for report type GENOME.");
        if (! this.genomeFile.canRead())
            throw new FileNotFoundException("Genome file " + this.genomeFile + " is not found or unreadable.");
    }

    @Override
    protected void writeHeader() {
        // Read in the genome.
        try {
            this.genome = new Genome(this.genomeFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.println("cluster\tfid\tgene\tfunction");
    }

    @Override
    public void writeCluster(Cluster cluster) {
        // Only proceed if the cluster is not a singleton.
        if (cluster.size() > 1) {
            // Update the cluster number and format a cluster ID.
            this.num++;
            String clNum = String.format("CL%d", num);
            // Now loop through the members, extracting data for each feature from the genome.
            for (String fid : cluster.getMembers()) {
                Feature feat = genome.getFeature(fid);
                String gene = "";
                String function;
                if (feat == null)
                    function = "** not found **";
                else {
                    gene = feat.getGeneName();
                    function = feat.getPegFunction();
                }
                this.formatln("%s\t%s\t%s\t%s", clNum, fid, gene, function);
            }
        }
    }

    @Override
    public void closeReport() {
    }

}
