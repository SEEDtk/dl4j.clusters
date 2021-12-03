/**
 *
 */
package org.theseed.reports;

import java.io.IOException;

import org.theseed.clusters.Cluster;
import org.theseed.clusters.ClusterGroup;

/**
 * This report produces a simple 2-column table.  It is fairly obtuse for human readers, but is used by
 * several existing scripts that analyze clustering.  Each row of the table contains a cluster ID and the
 * ID of a member.
 *
 * @author Bruce Parrello
 *
 */
public class TabularClusterReporter extends ClusterReporter {

    // FIELDS
    /** cluster number */
    private int clNum;

    /**
     * Construct a tabular cluster report.
     *
     * @param processor		controlling command processor
     */
    public TabularClusterReporter(IParms processor) {
    }

    @Override
    public void scanGroup(ClusterGroup mainGroup) throws IOException {
    }

    @Override
    protected void writeHeader() {
        this.println("cluster_id\tmember_id");
        this.clNum = 0;
    }

    @Override
    public void writeCluster(Cluster cluster) {
        this.clNum++;
        for (String member : cluster.getMembers())
            this.formatln("CL%d\t%s", clNum, member);
    }

    @Override
    public void closeReport() {
    }

}
