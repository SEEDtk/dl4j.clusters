/**
 *
 */
package org.theseed.reports;

import org.apache.commons.lang3.StringUtils;
import org.theseed.clusters.Cluster;
import org.theseed.clusters.ClusterGroup;

/**
 * The raw cluster report is extremely simple.  Each cluster is a single line with
 * the members in order, tab-delimited.
 *
 * @author Bruce Parrello
 *
 */
public class RawClusterReporter extends ClusterReporter {

    public RawClusterReporter(IParms processor) {
    }

    @Override
    protected void writeHeader() {
    }

    @Override
    public void writeCluster(Cluster cluster) {
        this.println(StringUtils.join(cluster.getMembers(), '\t'));
    }

    @Override
    public void closeReport() {
    }

    @Override
    public void scanGroup(ClusterGroup mainGroup) {
    }

}
