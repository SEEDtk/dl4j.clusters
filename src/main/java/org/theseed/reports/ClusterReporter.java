/**
 *
 */
package org.theseed.reports;

import java.io.File;
import java.io.IOException;

import org.theseed.clusters.Cluster;
import org.theseed.clusters.methods.ClusterMergeMethod;
import org.theseed.utils.ParseFailureException;

/**
 * This is the base class for cluster reports.  The essential function of this
 * report is to format clusters for output to a print file.
 *
 * @author Bruce Parrello
 *
 */
public abstract class ClusterReporter extends BaseReporterReporter {

    /**
     * This interface describes the parameters a command processor must support for these reports.
     */
    public interface IParms {

        /**
         * @return the name of a file containing a reference genome
         */
        File getGenomeFile();

        /**
         * @return the name of a file containing the various types of feature groups
         */
        File getGroupFile();

        /**
         * @return the clustering method
         */
        ClusterMergeMethod getMethod();

        /**
         * @return the minimum clustering similarity
         */
        double getMinSimilarity();

        /**
         * @return the prefix to put on the report title, or NULL if there is none
         */
        String getTitlePrefix();

        /**
         * @return the maximum allowed cluster size
         */
        int getMaxSize();

    }

    /**
     * This enum describes the different report types.
     */
    public static enum Type {
        INDENTED {
            @Override
            public ClusterReporter create(IParms processor) {
                return new IndentedClusterReporter(processor);
            }
        }, RAW {
            @Override
            public ClusterReporter create(IParms processor) {
                return new RawClusterReporter(processor);
            }
        }, GENOME {
            @Override
            public ClusterReporter create(IParms processor) throws IOException, ParseFailureException {
                return new GenomeClusterReporter(processor);
            }
        }, ANALYTICAL {
            @Override
            public ClusterReporter create(IParms processor) throws IOException, ParseFailureException {
                return new AnalyticalClusterReporter(processor);
            }
        };

        /**
         * @return a reporter of this type
         *
         * @param processor		controlling command processor
         *
         * @throws IOException
         * @throws ParseFailureException
         */
        public abstract ClusterReporter create(IParms processor) throws IOException, ParseFailureException;
    }

    /**
     * Write a cluster.
     *
     * @param cluster	cluster to output
     */
    public abstract void writeCluster(Cluster cluster);

    /**
     * Finish the report.
     */
    public abstract void closeReport();

}
