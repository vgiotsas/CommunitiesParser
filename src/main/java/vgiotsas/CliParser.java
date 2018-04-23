package vgiotsas;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import static org.kohsuke.args4j.ExampleMode.ALL;

class CliParser {

    @Option(name = "--period",
            required=true,
            usage="Time period for which BGP data will be collected.\nFormat: YYYYMMDD.hhmm,YYYYMMDD.hhmm")
    private String period = "";

    @Option(name="--communities",
            required=true,
            usage="Comma-separated list of 32-bit BGP Community values.")
    private String communities = "";

    @Option(name="--collectors",
            required=false,
            usage="Comma-separated list of BGP Collectors.")
    private String collectors = "all";

    @Option(name="--outdir",
            required=false,
            usage="Path to the output directory.")
    private String outdir = ".";

    @Option(name="--facilities",
            required=false,
            usage="Comma-separated list of facility names to restrict the scope of the analyzed AS links.")
    private String facilities = "all";

    /**
     *
     * @param dateString a string that represents a human readable datetime in format yyyyMMdd.hh (e.g. 20180224.0117)
     * @return long the epoch timestamp of the @dateString parameter
     */
    private long dateToEpoch(String dateString){
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd.hhmm");
        Date date = null;
        try {
            date = dateFormat.parse(dateString);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return (date != null ? date.getTime() : 0) /1000;
    }

    /**
     * Parses the user-provided command-line arguments
     * @param args The list of the provided command-line arguments
     * @return ArrayList<String> The list of lines in the response body
     */
    HashMap<String, String> cliParser(String[] args){
        CmdLineParser parser = new CmdLineParser(this);
        try {
            // parse the arguments.
            parser.parseArgument(args);
        } catch( CmdLineException e ) {
            System.err.println(e.getMessage());
            System.err.println("java BGPCommunitiesParser.jar [options...] arguments...");
            // print the list of available options
            parser.printUsage(System.err);
            System.err.println();

            // print option sample. This is useful some time
            System.err.println("  Example: java BGPCommunitiesParser.jar"+parser.printExample(ALL));
            System.exit(0);
        }

        HashMap<String, String> cliArgs = new HashMap<>();
        String[] period;
        long startTs = 0;
        long endTs = 0;
        // parse the period argument
        try{
            period = this.period.split(",");
            startTs = this.dateToEpoch(period[0]);
            endTs = this.dateToEpoch(period[1]);
            if (startTs >= endTs){
                System.err.println("The period argument is invalid. " +
                        "The start datetime should be before the end datetime.");
                System.exit(-1);
            }
        }
        catch (java.lang.ArrayIndexOutOfBoundsException e) {
            System.err.println("The period argument is invalid. " +
                    "Please provide two comma-separated datetimes in the format YYYMMMDD.hhmm " +
                    "(e.g. 20180124.0127,20180125.1010).");
            System.exit(-1);
        }

        cliArgs.put("communities", this.communities);
        cliArgs.put("start", Long.toString(startTs));
        cliArgs.put("end", Long.toString(endTs));
        cliArgs.put("collectors", this.collectors);
        cliArgs.put("outdir", this.outdir);
        cliArgs.put("facilities", this.facilities);
        return cliArgs;
    }
}
