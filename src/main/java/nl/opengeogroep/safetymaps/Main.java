package nl.opengeogroep.safetymaps;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import nl.opengeogroep.safetymaps.server.stripes.ViewerApiActionBean;
import nl.opengeogroep.safetymaps.server.stripes.VrhActionBean;
import nl.opengeogroep.safetymaps.server.stripes.VrlnActionBean;
import nl.opengeogroep.safetymaps.viewer.ViewerDataExporter;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
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
        commands.addOption(Option.builder("organisation")
                    .desc("Write api/organisation.json")
                    .build());
        commands.addOption(Option.builder("etag")
                    .desc("Get cache ETag for creator viewer objects list")
                    .build());
        commands.addOption(Option.builder("creator")
                    .desc("Write safetymaps_creator module JSON to api dir")
                    .build());
        commands.addOption(Option.builder("object")
                    .hasArg()
                    .argName("id")
                    .desc("Write single creator object details JSON to stdout")
                    .build());
        commands.addOption(Option.builder("vrh")
                .desc("Write vrh_objects module JSON to api dir")
                .build());
        commands.addOption(Option.builder("vrln")
                .desc("Write vrln brandkranen module JSON to api dir")
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

        int indent = Integer.parseInt(cl.getOptionValue("indent", "0"));

        try {
            if(cl.hasOption("organisation")) {
                cmdWriteOrganisation(c, indent);
            }
            if(cl.hasOption("etag")) {
                cmdGetEtag(c);
            }
            if(cl.hasOption("creator")) {
                cmdWriteCreator(c, indent);
            }
            if(cl.hasOption("object")) {
                cmdWriteObject(c, Integer.parseInt(cl.getOptionValue("object")), indent);
            }
            if(cl.hasOption("vrh")) {
                cmdWriteVrh(c, indent);
            }
            if(cl.hasOption("vrln")) {
                cmdWriteVrln(c, indent);
            }
            System.err.println("No command specified");
            System.exit(1);
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void cmdWriteOrganisation(Connection c, int indent) throws Exception {
        JSONObject o = ViewerApiActionBean.getOrganisation(c, 28992);
        FileUtils.writeByteArrayToFile(new File("api/organisation.json"), o.toString(indent).getBytes("UTF-8"));
        System.exit(0);
    }

    private static void cmdGetEtag(Connection c) throws Exception {
        ViewerDataExporter vde = new ViewerDataExporter(c);
        System.out.println(vde.getObjectsETag());
        System.exit(0);
    }

    private static void cmdWriteCreator(Connection c, int indent) throws Exception {
        ViewerDataExporter vde = new ViewerDataExporter(c);

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
        FileUtils.writeByteArrayToFile(new File("api/features.json"), o.toString(indent).getBytes("UTF-8"));

        writeCreatorObjects(vde, indent);

        writeCreatorStyles(vde, indent);

        writeCreatorLibrary(c, indent);

        System.exit(success ? 0 : 1);
    }

    private static void writeCreatorObjects(ViewerDataExporter vde, int indent) throws Exception {
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
            }
        }
    }

    private static void writeCreatorStyles(ViewerDataExporter vde, int indent) throws Exception {
        JSONObject o = vde.getStyles();

        String file = "api/styles.json";
        try(FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(o.toString(indent).getBytes("UTF-8"));
        } catch(Exception e) {
            System.err.println("Error writing object JSON to " + file);
            e.printStackTrace();
        }
    }

    private static void writeCreatorLibrary(Connection c, int indent) throws Exception {
        JSONObject o = ViewerApiActionBean.getLibrary(c);
        FileUtils.writeByteArrayToFile(new File("api/library.json"), o.toString(indent).getBytes("UTF-8"));
    }

    private static void cmdWriteObject(Connection c, int id, int indent) throws Exception {
        ViewerDataExporter vde = new ViewerDataExporter(c);
        JSONObject o = vde.getViewerObjectDetails(id);
        System.out.println(o.toString(indent));
        System.exit(0);
    }

    private static void cmdWriteVrh(Connection c, int indent) throws Exception {

        boolean fail = false;

        JSONObject o = new JSONObject();
        JSONArray dbks = new JSONArray();
        boolean success = true;
        try {
            dbks = VrhActionBean.dbksJson(c);
            o.put("results", dbks);
        } catch(Exception e) {
            success = false;
            fail = true;
            String msg = e.getClass() + ": " + e.getMessage();
            if(e.getCause() != null) {
                msg += ", " + e.getCause().getMessage();
            }
            o.put("error", msg);
            System.err.println("Fout bij ophalen DBKs");
            e.printStackTrace();
        }
        o.put("success", success);
        FileUtils.writeByteArrayToFile(new File("api/vrh/dbks.json"), o.toString(indent).getBytes("UTF-8"));

        for(int i = 0; i < dbks.length(); i++) {
            int id = dbks.getJSONObject(i).getInt("id");
            o.put("results", VrhActionBean.dbkJson(c, id));
            FileUtils.writeByteArrayToFile(new File("api/vrh/dbk/" + id + ".json"), o.toString(indent).getBytes("UTF-8"));
        }

        o = new JSONObject();
        JSONArray evenementen = new JSONArray();
        success = true;
        try {
            evenementen = VrhActionBean.evenementenJson(c);
            o.put("results", evenementen);
        } catch(Exception e) {
            success = false;
            fail = true;
            String msg = e.getClass() + ": " + e.getMessage();
            if(e.getCause() != null) {
                msg += ", " + e.getCause().getMessage();
            }
            o.put("error", msg);
            System.err.println("Fout bij ophalen evenementen");
            e.printStackTrace();
        }
        o.put("success", success);
        FileUtils.writeByteArrayToFile(new File("api/vrh/evenementen.json"), o.toString(indent).getBytes("UTF-8"));

        for(int i = 0; i < evenementen.length(); i++) {
            int id = evenementen.getJSONObject(i).getInt("id");
            o.put("results", VrhActionBean.evenementJson(c, id));
            FileUtils.writeByteArrayToFile(new File("api/vrh/evenement/" + id + ".json"), o.toString(indent).getBytes("UTF-8"));
        }

        o = new JSONObject();
        JSONArray waterongevallen = new JSONArray();
        success = true;
        try {
            waterongevallen = VrhActionBean.waterongevallenJson(c);
            o.put("results", waterongevallen);
        } catch(Exception e) {
            success = false;
            fail = true;
            String msg = e.getClass() + ": " + e.getMessage();
            if(e.getCause() != null) {
                msg += ", " + e.getCause().getMessage();
            }
            o.put("error", msg);
            System.err.println("Fout bij ophalen waterongevallen");
            e.printStackTrace();
        }
        o.put("success", success);
        FileUtils.writeByteArrayToFile(new File("api/vrh/waterongevallen.json"), o.toString(indent).getBytes("UTF-8"));

        for(int i = 0; i < waterongevallen.length(); i++) {
            int id = waterongevallen.getJSONObject(i).getInt("id");
            o.put("results", VrhActionBean.waterongevallenkaartJson(c, id));
            FileUtils.writeByteArrayToFile(new File("api/vrh/waterongevallenkaart/" + id + ".json"), o.toString(indent).getBytes("UTF-8"));
        }

        System.exit(success ? 0 : 1);
    }

    private static void cmdWriteVrln(Connection c, int indent) throws Exception {
        JSONObject o = VrlnActionBean.getData(c, null);
        FileUtils.writeByteArrayToFile(new File("api/vrln/brandkranen.json"), o.toString(indent).getBytes("UTF-8"));
        System.exit(0);
    }
}
