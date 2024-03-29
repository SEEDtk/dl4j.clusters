/**
 *
 */
package org.theseed.reports;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.basic.ParseFailureException;
import org.theseed.clusters.Cluster;
import org.theseed.clusters.ClusterGroup;
import org.theseed.clusters.methods.ClusterMergeMethod;

/**
 * This is the base class for cluster reports.  The essential function of this
 * report is to format clusters for output to a print file.
 *
 * @author Bruce Parrello
 *
 */
public abstract class ClusterReporter extends BaseReporterReporter {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(ClusterReporter.class);


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

        /**
         * @return the recommended batch size for queries
         */
        int getBatchSize();

        /**
         * @return the output file for subsystem ID mappings
         */
        File getSubFile();

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
        }, FEATURES {
            @Override
            public ClusterReporter create(IParms processor) throws IOException, ParseFailureException {
                return new AnalyticalClusterReporter(processor);
            }
        }, SAMPLES {
            @Override
            public ClusterReporter create(IParms processor) throws IOException, ParseFailureException {
                return new SampleClusterReporter(processor);
            }
        }, TABULAR {
            @Override
            public ClusterReporter create(IParms processor) {
                return new TabularClusterReporter(processor);
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
     * Scan the cluster group in advance of the report to allow computing custom information for the
     * report.
     *
     * @param mainGroup		cluster group being reported
     *
     * @throws IOException
     */
    public abstract void scanGroup(ClusterGroup mainGroup) throws IOException;

    /**
     * Write a cluster.
     *
     * @param cluster	cluster to output
     */
    public abstract void writeCluster(Cluster cluster);

    /**
     * Finish the report.
     *
     * @throws IOException
     */
    public abstract void closeReport() throws IOException;

}
