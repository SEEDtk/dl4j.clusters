package org.theseed.dl4j.clusters;

import java.util.Arrays;

import org.theseed.utils.BaseProcessor;

/**
 * This command group contains services related to unsupervised clustering.
 *
 * 	cluster		perform agglomeration clustering
 *  freq		perform frequency analysis of correlations
 *
 * @author Bruce Parrello
 *
 */
public class App {

    public static void main( String[] args )
    {
        // Get the control parameter.
        String command = args[0];
        String[] newArgs = Arrays.copyOfRange(args, 1, args.length);
        BaseProcessor processor;
        // Determine the command to process.
        switch (command) {
        case "cluster" :
            processor = new ClusterProcessor();
            break;
        case "freq" :
            processor = new CorrFreqProcessor();
            break;
        default:
            throw new RuntimeException("Invalid command " + command);
        }
        // Process it.
        boolean ok = processor.parseCommand(newArgs);
        if (ok) {
            processor.run();
        }
    }

}
