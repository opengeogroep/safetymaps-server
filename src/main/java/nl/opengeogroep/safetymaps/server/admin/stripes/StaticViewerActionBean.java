package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.*;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import nl.opengeogroep.safetymaps.server.db.DB;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;

/**
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/admin/action/static")
public class StaticViewerActionBean implements ActionBean {
    private ActionBeanContext context;

    private static final Log log = LogFactory.getLog("cfg");

    private static final String JSP = "/WEB-INF/jsp/admin/static.jsp";

    private Date lastConfigUpdate;
    private Date lastStaticUpdate;

    private boolean updateRequired;

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public Date getLastConfigUpdate() {
        return lastConfigUpdate;
    }

    public void setLastConfigUpdate(Date lastConfigUpdate) {
        this.lastConfigUpdate = lastConfigUpdate;
    }

    public Date getLastStaticUpdate() {
        return lastStaticUpdate;
    }

    public void setLastStaticUpdate(Date lastStaticUpdate) {
        this.lastStaticUpdate = lastStaticUpdate;
    }

    public boolean isUpdateRequired() {
        return updateRequired;
    }

    @DefaultHandler
    public Resolution info() throws NamingException, SQLException {
        lastConfigUpdate = Cfg.getLastUpdated();

        File outputDir = Cfg.getPath("static_outputdir");

        try {
            lastStaticUpdate = new Date(outputDir.lastModified());

            updateRequired = lastStaticUpdate.before(lastConfigUpdate);
        } catch(Exception e) {
            String s = "Error getting last static update: " + e.getClass() + ": " + e.getMessage();
            log.warn(s);
            if(log.isDebugEnabled()) {
                log.debug(s, e);
            }
        }

        return new ForwardResolution(JSP);
    }

    public Resolution update() throws NamingException, SQLException, IOException {
        // TODO check update flagfile option

        final List<String> commands = new ArrayList<>();
        String s = Cfg.getSetting("static_update_command");
        Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(s);
        while(m.find()) {
            commands.add(m.group(1).replace("\"",""));
        }

        log.info("Updating static app using command " + commands);

        ProcessBuilder builder = new ProcessBuilder(commands);
        builder.redirectErrorStream(true);
        final Process process = builder.start();
        final InputStream stdout = process.getInputStream();

        return new StreamingResolution("text/plain") {
            @Override
            public void stream(HttpServletResponse response) throws IOException  {
                OutputStream out = response.getOutputStream();
                out.write(("Start update script " + StringUtils.join(commands, " ") + "...\n\n").getBytes());
                out.flush();
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                IOUtils.copy(stdout, new TeeOutputStream(output, out));
                try {
                    log.info("Waiting for script");
                    process.waitFor();
                    log.info("Script output: " + new String(output.toByteArray()));
                    log.info("Update script exit code: " + process.exitValue());
                    out.write(("\n\nScript exit code: " + process.exitValue()).getBytes());
                    out.flush();
                } catch(InterruptedException e) {
                }
            }
        };
    }

    public Resolution updateAutoReloadSequence() throws Exception {

        Object options = DB.qr().query("select options from organisation.modules where name = 'autoreload'", new ScalarHandler<Object>());

        if(options == null) {
            options = "{ \"sequence\": 0}";
        }

        JSONObject j = new JSONObject(options.toString());
        int sequence = j.getInt("sequence");
        j.put("sequence", sequence+1);

        DB.qr().update("update organisation.modules set options = ?::json where name = 'autoreload'", j.toString());
        getContext().getMessages().add(new SimpleMessage("Sequence geupdate naar " + j.getInt("sequence") + "!"));
        return info();
    }
}
