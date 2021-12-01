/**
 *
 */
package org.theseed.reports;

import org.theseed.clusters.Cluster;

/**
 * The indented report specifies useful information about the cluster on a header line and
 * then displays the member IDs one per line, indented, below that.
 *
 * @author Bruce Parrello
 *
 */
public class IndentedClusterReporter extends ClusterReporter {

    // FIELDS
    /** cluster number */
    private int num;

    public IndentedClusterReporter(IParms processor) {
    }

    @Override
    protected void writeHeader() {
        this.num = 0;
    }

    @Override
    public void writeCluster(Cluster cluster) {
        // Update the cluster number.
        num++;
        // Write the header line.
        this.formatln("CL%d: size = %d, height = %d, score = %1.4f",  num, cluster.size(),
                cluster.getHeight(), cluster.getScore());
        // Write the members.
        for (String member : cluster.getMembers())
            this.println("\t" + member);
        // Write a spacer.
        this.println();
    }

    @Override
    public void closeReport() {
    }

}
