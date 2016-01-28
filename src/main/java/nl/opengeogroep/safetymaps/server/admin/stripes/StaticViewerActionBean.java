package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Date;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.ForwardResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author Matthijs Laan
 */
public class StaticViewerActionBean implements ActionBean {
    private ActionBeanContext context;

    private static final Log log = LogFactory.getLog("cfg");

    private static final String JSP = "/WEB-INF/jsp/admin/offline.jsp";

    private Date lastConfigUpdate;
    private Date lastOfflineUpdate;

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

    public Date getLastOfflineUpdate() {
        return lastOfflineUpdate;
    }

    public void setLastOfflineUpdate(Date lastOfflineUpdate) {
        this.lastOfflineUpdate = lastOfflineUpdate;
    }

    @DefaultHandler
    public Resolution info() throws NamingException, SQLException {
        lastConfigUpdate = Cfg.getLastUpdated();

        File outputDir = Cfg.getPath("static_outputdir");

        try {
            System.out.println(outputDir + ";" + outputDir.lastModified());
            lastOfflineUpdate = new Date(outputDir.lastModified());
        } catch(Exception e) {
            String s = "Error getting last offline update: " + e.getClass() + ": " + e.getMessage();
            log.warn(s);
            if(log.isDebugEnabled()) {
                log.debug(s, e);
            }
        }

        return new ForwardResolution(JSP);
    }

    public Resolution update() throws NamingException, SQLException, IOException {
        // TODO check update flagfile option

        final File updateScript = Cfg.getPath("static_update_script");

        log.info("Updating static app using script " + updateScript);

        ProcessBuilder builder = new ProcessBuilder(updateScript.toString());
        builder.redirectErrorStream(true);
        final Process process = builder.start();
        final InputStream stdout = process.getInputStream();

        return new StreamingResolution("text/plain") {
            @Override
            public void stream(HttpServletResponse response) throws IOException  {
                OutputStream out = response.getOutputStream();
                out.write(("Start update script " + updateScript + "...\n\n").getBytes());
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
}
