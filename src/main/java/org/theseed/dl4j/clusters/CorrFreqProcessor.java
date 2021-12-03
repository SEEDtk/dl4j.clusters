/**
 *
 */
package org.theseed.dl4j.clusters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.AxisCrosses;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.MarkerStyle;
import org.apache.poi.xddf.usermodel.chart.XDDFChartLegend;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFScatterChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.io.TabbedLineReader;
import org.theseed.utils.BaseProcessor;
import org.theseed.utils.ParseFailureException;

/**
 * This command accepts as input a set of correlations.  We wish to analyze the distribution
 * to determine the point at which the higher correlations begin to occur with abnormal frequency.
 * To do this, we assume a normal distribution of correlations around 0.  The actual cumulative
 * frequency is then compared to the expected cumulative frequency at each point.
 *
 * For reporting purposes, we will divide the correlation range into a specified
 * number of buckets.  We compare the number of observations to the left of each bucket
 * with the expected cumulative number and focus on the difference.
 *
 * Note that the specified range minimum and maximum should be the theoretical maximum and minimum,
 * not necessarily the actual ones.  So, for a normalized mean squared error they would be 0.0 and 1.0,
 * even if the input does not contain anything over 0.7.
 *
 * The input file will be read from the standard input.
 *
 * The positional parameters are the name of the column containing the correlation numbers and the
 * name of the output file, which will be an Excel spreadsheet.
 *
 * The command-line options are as follows.
 *
 * -h	display command-line usage
 * -v	display more frequent log messages
 * -i	input file (if not STDIN)
 * -q	number of buckets into which the range should be divided (default 100)
 *
 * --min		minimum input value (default -1)
 * --max		maximum input value (default 1)
 * --midpoint	if TRUE, the mean will be forced to the midpoint of the input range; otherwise it will be computed
 * --format		output format for the report (default RAW)
 *
 * @author Bruce Parrello
 *
 */
public class CorrFreqProcessor extends BaseProcessor  {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(CorrFreqProcessor.class);
    /** width of a bucket */
    private double bucketWidth;
    /** array of bucket counts */
    private int[] buckets;
    /** array of bucket limits:  bucket[i] contains values <= limits[i] */
    private double[] limits;
    /** statistics object for computing mean and deviation */
    private SummaryStatistics stats;
    /** index of the input column */
    private int inColIdx;
    /** number of input observations */
    private double obsCount;
    /** input stream */
    private TabbedLineReader inStream;
    /** cell style for spreadsheet numbers */
    private CellStyle numStyle;

    // COMMAND-LINE OPTIONS

    /** input file (if not STDIN) */
    @Option(name = "--input", aliases = { "-i" }, metaVar = "inFile.tbl", usage = "input file (if not STDIN)")
    private File inFile;

    /** number of buckets */
    @Option(name = "--buckets", aliases = { "-q" }, metaVar = "50", usage = "number of buckets in which to divide the range")
    private int nBuckets;

    /** minimum input value */
    @Option(name = "--min", metaVar = "0.0", usage ="minimum of input range")
    private double rangeMin;

    /** maximum input value */
    @Option(name = "--max", metaVar = "100.0", usage = "maximum of input range")
    private double rangeMax;

    /** if TRUE, the mean will be forced to the midpoint of the input range; otherwise, it will be computed */
    @Option(name = "--midpoint", usage = "if specified, the mean will be forced to the midpoint for the expected distribution")
    private boolean useMidPoint;

    /** index (1-based) or name of input column */
    @Argument(index = 0, metaVar = "colName", usage = "index (1-based) or name of input column", required = true)
    private String colName;

    /** name of output file */
    @Argument(index = 1, metaVar = "outFile.xlsx", usage = "name of output spreadsheet file", required = true)
    private File outFile;


    @Override
    protected void setDefaults() {
        this.inFile = null;
        this.nBuckets = 100;
        this.rangeMin = -1.0;
        this.rangeMax = 1.0;
        this.useMidPoint = false;
    }

    @Override
    protected boolean validateParms() throws IOException, ParseFailureException {
        // Compute the bucket width.
        if (this.nBuckets < 10)
            throw new ParseFailureException("Bucket count (q) must be 10 or more.");
        // Validate the range limits.
        if (this.rangeMax <= this.rangeMin)
            throw new ParseFailureException("Range maximum must be greater than the minimum.");
        this.bucketWidth = (this.rangeMax - this.rangeMin) / this.nBuckets;
        log.info("Bucket width is {}.", this.bucketWidth);
        // Allocate the bucket arrays.
        this.buckets = new int[this.nBuckets];
        this.limits = new double[this.nBuckets];
        // Initialize the arrays.  Note we multiply here to reduce the accumulation of roundoff.
        for (int i = 0; i < this.nBuckets; i++) {
            this.limits[i] = (i + 1) * this.bucketWidth + this.rangeMin;
            this.buckets[i] = 0;
        }
        // Create the statistics object.
        this.stats = new SummaryStatistics();
        // Open the input stream.
        if (this.inFile == null) {
            log.info("Correlations will be read from the standard input.");
            this.inStream = new TabbedLineReader(System.in);
        } else {
            log.info("Correlations will be read from {}.", this.inFile);
            this.inStream = new TabbedLineReader(this.inFile);
        }
        // Find the input column index.
        this.inColIdx = this.inStream.findField(this.colName);
        return true;
    }

    @Override
    protected void runCommand() throws Exception {
        try {
            // Loop through the input file, accumulating observations in buckets.
            int count = 0;
            int errors = 0;
            for (TabbedLineReader.Line line : this.inStream) {
                double value = line.getDouble(this.inColIdx);
                // We have to eliminate non-finite values:  they mess everything up.
                if (! Double.isFinite(value))
                    errors++;
                else {
                    int idx = (int) ((value - this.rangeMin) / this.bucketWidth);
                    // Insure the input value is valid.
                    if (idx > this.nBuckets || idx < 0) {
                        log.warn("Value {} out of range.", value);
                        errors++;
                    } else {
                        // We need to adjust the array index.  The distribution service computes
                        // probability <= X, so if we are at the limit of the previous bucket,
                        // we subtract 1.
                        if (idx > 0 && value <= this.limits[idx-1]) idx--;
                        // Store this value in the bucket.
                        this.buckets[idx]++;
                        count++;
                        if (count % 5000 == 0)
                            log.info("{} observations processed.", count);
                        // Record it in the stats object.
                        this.stats.addValue(value);
                    }
                }
            }
            log.info("{} total observations, {} errors.", count, errors);
            this.obsCount = (double) count;
            // Compute the mean and standard devation.
            double mean = this.stats.getMean();
            double sdev = this.stats.getStandardDeviation();
            log.info("Mean is {}, standard deviation is {}.", mean, sdev);
            // We compute a distribution with the specified standard
            // deviation.
            double usableMean = (this.useMidPoint ? (this.rangeMax + this.rangeMin) / 2.0 : mean);
            NormalDistribution dist = new NormalDistribution(null, usableMean, sdev);
            // Save the base expectation value.
            double oldExpected = dist.cumulativeProbability(-1.0);
            // Now we write the reports for the buckets.
            try (XSSFWorkbook wb = new XSSFWorkbook()) {
                XSSFSheet sheet = wb.createSheet("frequency");
                // Create the special formatting styles.
                DataFormat format = wb.createDataFormat();
                short dblFmt = format.getFormat("##0.0000");
                this.numStyle = wb.createCellStyle();
                this.numStyle.setDataFormat(dblFmt);
                this.numStyle.setAlignment(HorizontalAlignment.RIGHT);
                // Create the heading cells.
                Row headRow = sheet.createRow(0);
                headRow.createCell(0, CellType.STRING).setCellValue("limit");
                headRow.createCell(1, CellType.STRING).setCellValue("expected");
                headRow.createCell(2, CellType.STRING).setCellValue("actual");
                // Loop through the buckets.
                for (int i = 0; i < this.nBuckets; i++) {
                    // Get the values for this bucket.
                    double actual = this.buckets[i] / this.obsCount;
                    double newExpected = dist.cumulativeProbability(this.limits[i]);
                    double expected = newExpected - oldExpected;
                    // Store them in the sheet.
                    Row dataRow = sheet.createRow(i+1);
                    this.createNumCell(dataRow, 0, this.limits[i]);
                    this.createNumCell(dataRow, 1, expected);
                    this.createNumCell(dataRow, 2, actual);
                    // Set up for the next time.
                    oldExpected = newExpected;
                }
                // Finish the report by creating the graph.
                XSSFDrawing drawing = sheet.createDrawingPatriarch();
                // Set up the space to contain the graph.  We occupy 10 columns and 22 rows starting in E2.
                XSSFClientAnchor occupied_space = drawing.createAnchor(0, 0, 0, 0, 4, 1, 14, 23);
                // Define the chart
                XSSFChart lineChart = drawing.createChart(occupied_space);
                lineChart.setTitleText("Frequency Distribution of Similarities");
                XDDFChartLegend legend = lineChart.getOrAddLegend();
                legend.setPosition(LegendPosition.BOTTOM);
                XDDFValueAxis bottomAxis = lineChart.createValueAxis(AxisPosition.BOTTOM);
                bottomAxis.setTitle("Similarity Value");
                bottomAxis.setMinimum(this.rangeMin);
                bottomAxis.setMaximum(this.rangeMax);
                XDDFValueAxis leftAxis = lineChart.createValueAxis(AxisPosition.LEFT);
                leftAxis.setTitle("Frequency");
                leftAxis.setPosition(AxisPosition.LEFT);
                leftAxis.setCrosses(AxisCrosses.MIN);
                // Set up the data.
                XDDFScatterChartData chartData = (XDDFScatterChartData) lineChart.createData(ChartTypes.SCATTER, bottomAxis, leftAxis);
                XDDFNumericalDataSource<Double> xSeries =
                        XDDFDataSourcesFactory.fromNumericCellRange(sheet, new CellRangeAddress(1, this.nBuckets, 0, 0));
                XDDFNumericalDataSource<Double> y1Series =
                        XDDFDataSourcesFactory.fromNumericCellRange(sheet, new CellRangeAddress(1, this.nBuckets, 1, 1));
                XDDFNumericalDataSource<Double> y2Series =
                        XDDFDataSourcesFactory.fromNumericCellRange(sheet, new CellRangeAddress(1, this.nBuckets, 2, 2));
                this.addSeries(chartData, xSeries, y1Series, "expected");
                this.addSeries(chartData, xSeries, y2Series, "actual");
                // Plot the graph.
                lineChart.plot(chartData);
                // Update the workbook.
                try (FileOutputStream outStream = new FileOutputStream(this.outFile)) {
                    log.info("Saving workbook to {}.", this.outFile);
                    wb.write(outStream);
                }
            }
        } finally {
            if (this.inStream != null)
                inStream.close();
        }

    }

    /**
     * Add a series of data to a scatter chart.
     *
     * @param chartData		scatter chart's data descriptor
     * @param xSeries		x-axis series
     * @param ySeries		corresponding data values
     */
    private void addSeries(XDDFScatterChartData chartData, XDDFNumericalDataSource<Double> xSeries,
            XDDFNumericalDataSource<Double> ySeries, String title) {
        XDDFScatterChartData.Series dataSeries = (XDDFScatterChartData.Series) chartData.addSeries(xSeries, ySeries);
        dataSeries.setTitle(title, null);
        dataSeries.setMarkerStyle(MarkerStyle.NONE);
    }

    /**
     * Create a cell at the specified position in the specified row and put the specified number in it.
     *
     * @param dataRow	target row
     * @param c			index of the target column
     * @param value		value to put in the cell
     */
    private void createNumCell(Row dataRow, int c, double value) {
        Cell cell = dataRow.createCell(c, CellType.NUMERIC);
        cell.setCellValue(value);
        cell.setCellStyle(this.numStyle);
    }

}
