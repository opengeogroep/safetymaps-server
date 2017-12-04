package nl.opengeogroep.safetymaps;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import nl.opengeogroep.safetymaps.viewer.ViewerDataExporter;
import org.apache.commons.cli.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Matthijs Laan
 */
public class Main {

    private static Options buildOptions() {
        Options options = new Options();
        options.addOption("h", false, "Show this help");
        options.addOption(Option.builder("db")
                .required()
                .hasArg()
                .argName("jdbc-url")
                .desc("JDBC URL for SafetyMaps database. Set PGUSER and PGPASSWORD environment variables to credentials")
                .build());
        options.addOption(Option.builder("indent")
                .hasArg()
                .desc("JSON indent")
                .build());

        OptionGroup commands = new OptionGroup();
        commands.setRequired(true);
        commands.addOption(Option.builder("etag")
                    .desc("Get cache ETag for viewer objects list")
                    .build());
        commands.addOption(Option.builder("exportoverview")
                    .desc("Write viewer overview JSON to 'api/features.json'")
                    .build());
        commands.addOption(Option.builder("exportobjects")
                    .desc("Write all objects JSON to 'api/object/<id>.json'")
                    .build());
        commands.addOption(Option.builder("exportstyles")
                    .desc("Write styles JSON to 'api/styles.json'")
                    .build());
        commands.addOption(Option.builder("object")
                    .hasArg()
                    .argName("id")
                    .desc("Write single object details JSON to stdout")
                    .build());
        options.addOptionGroup(commands);

        return options;
    }

    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("safetymaps-cli", options );
    }


    public static void main(String[] args) throws Exception {

        Options options = buildOptions();
        CommandLine cl = null;
        try {
            CommandLineParser parser = new DefaultParser();

            cl = parser.parse(options, args);
        } catch(ParseException e) {
            System.out.printf("%s\n\n", e.getMessage());

            printHelp(options);
            System.exit(1);
        }

        Class.forName("org.postgresql.Driver");

        String user = System.getenv("PGUSER");
        String pass = System.getenv("PGPASSWORD");
        Connection c = DriverManager.getConnection(cl.getOptionValue("db"), user, pass);

        ViewerDataExporter vde = new ViewerDataExporter(c);

        int indent = Integer.parseInt(cl.getOptionValue("indent", "0"));

        if(cl.hasOption("etag")) {
            cmdGetEtag(vde);
        }
        if(cl.hasOption("exportoverview")) {
            cmdWriteOverview(vde, indent);
        }
        if(cl.hasOption("exportobjects")) {
            cmdWriteObjects(vde, indent);
        }
        if(cl.hasOption("exportstyles")) {
            cmdWriteStyles(vde, indent);
        }
        if(cl.hasOption("object")) {
            cmdWriteObject(vde, Integer.parseInt(cl.getOptionValue("object")), indent);
        }
        System.err.println("No command specified");
        System.exit(1);
    }

    private static void cmdGetEtag(ViewerDataExporter vde) throws Exception {
        System.out.println(vde.getObjectsETag());
        System.exit(0);
    }

    private static void cmdWriteOverview(ViewerDataExporter vde, int indent) throws Exception {
        new File("api").mkdir();
        try(FileOutputStream fos = new FileOutputStream("api/features.json")) {
            JSONObject o = new JSONObject();
            boolean success = true;
            try {
                JSONArray a = vde.getViewerObjectMapOverview();
                o.put("results", a);
            } catch(Exception e) {
                success = true;
                String msg = e.getClass() + ": " + e.getMessage();
                if(e.getCause() != null) {
                    msg += ", " + e.getCause().getMessage();
                }
                o.put("error", msg);
                System.err.println("Error writing viewer overview JSON");
                e.printStackTrace();
            }
            o.put("success", success);
            fos.write(o.toString(indent).getBytes("UTF-8"));
            System.exit(success ? 0 : 1);
        }
    }

    private static void cmdWriteObjects(ViewerDataExporter vde, int indent) throws Exception {
        List<JSONObject> objects = vde.getAllViewerObjectDetails();

        new File("api/object").mkdirs();

        for(JSONObject o: objects) {
            Integer id = o.getInt("id");
            String file = "api/object/" + id + ".json";
            try(FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(o.toString(indent).getBytes("UTF-8"));
            } catch(Exception e) {
                System.err.println("Error writing object JSON to " + file);
                e.printStackTrace();
                System.exit(1);
            }
        }
        System.exit(0);
    }

    private static void cmdWriteStyles(ViewerDataExporter vde, int indent) throws Exception {
        JSONObject o = vde.getStyles();

        String file = "api/styles.json";
        try(FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(o.toString(indent).getBytes("UTF-8"));
        } catch(Exception e) {
            System.err.println("Error writing object JSON to " + file);
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private static void cmdWriteObject(ViewerDataExporter vde, int id, int indent) throws Exception {
        JSONObject o = vde.getViewerObjectDetails(id);
        System.out.println(o.toString(indent));
        System.exit(0);
    }
}
