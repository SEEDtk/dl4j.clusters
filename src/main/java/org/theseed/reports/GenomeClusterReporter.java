/**
 *
 */
package org.theseed.reports;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.lang3.StringUtils;
import org.theseed.clusters.Cluster;
import org.theseed.clusters.ClusterGroup;
import org.theseed.genome.Feature;
import org.theseed.genome.Genome;
import org.theseed.genome.SubsystemRow;
import org.theseed.magic.MagicMap;
import org.theseed.subsystems.SubsystemRowDescriptor;
import org.theseed.utils.ParseFailureException;

/**
 * This is a special report for clusters of features in a genome.  The genome GTO
 * is specified as a parameter for the command processor, and when each cluster is
 * written, the gene name, the subsystems, and the feature role are included.
 *
 * @author Bruce Parrello
 *
 */
public class GenomeClusterReporter extends ClusterReporter {

    // FIELDS
    /** file containing the reference genome */
    private File genomeFile;
    /** output file for subsystem definitions */
    private File subFile;
    /** current cluster number */
    private int num;
    /** reference genome */
    private Genome genome;
    /** subsystem ID map */
    private MagicMap<SubsystemRowDescriptor> subMap;
    /** empty subsystem array */
    private static final Set<String> NO_SUBS = Collections.emptySet();

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
        this.subFile = processor.getSubFile();
        if (this.subFile == null)
            log.info("Subsystem mapping will not be output.");
        else
            log.info("Subsystem mapping will be written to {}.", this.subFile);
        this.subMap = new MagicMap<SubsystemRowDescriptor>(new SubsystemRowDescriptor());
    }

    @Override
    protected void writeHeader() {
        // Read in the genome.
        try {
            this.genome = new Genome(this.genomeFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.println("cluster\tfid\tgene\tsubsystems\tfunction");
    }

    @Override
    public void writeCluster(Cluster cluster) {
        // Update the cluster number and format a cluster ID.
        this.num++;
        String clNum = String.format("CL%d", num);
        // Now loop through the members, extracting data for each feature from the genome.
        for (String fid : cluster.getMembers()) {
            Feature feat = genome.getFeature(fid);
            String gene = "";
            String function;
            Set<String> subsystems = NO_SUBS;
            if (feat == null)
                function = "** not found **";
            else {
                gene = feat.getGeneName();
                function = feat.getPegFunction();
                // Build the set of subsystem IDs.
                Collection<SubsystemRow> subs = feat.getSubsystemRows();
                if (! subs.isEmpty()) {
                    subsystems = new TreeSet<String>();
                    for (SubsystemRow sub : subs) {
                        // Check for an existing ID.  If none, create one.
                        SubsystemRowDescriptor desc = this.subMap.getByName(sub.getName());
                        if (desc == null)
                            desc = new SubsystemRowDescriptor(sub, this.subMap);
                        // Add the ID of the descriptor found to the ID list.
                        subsystems.add(desc.getId());
                    }
                }
            }
            String subString = StringUtils.join(subsystems, ',');
            this.formatln("%s\t%s\t%s\t%s\t%s", clNum, fid, gene, subString, function);
        }
    }

    @Override
    public void closeReport() throws IOException {
        // Write out the subsystem mapping, if requested.
        if (this.subFile != null) {
            log.info("Writing subsystem mapping to {}.", this.subFile);
            try (PrintWriter writer = new PrintWriter(this.subFile)) {
                writer.println("subsystem_id\tsubsystem_name");
                for (Map.Entry<String, String> sub : this.subMap.entrySet())
                    writer.println(sub.getKey() + "\t" + sub.getValue());
            }
        }
    }

    @Override
    public void scanGroup(ClusterGroup mainGroup) {
    }

}
