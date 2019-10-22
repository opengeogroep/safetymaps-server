/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.server.stripes;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.StrictBinding;
import net.sourceforge.stripes.action.UrlBinding;
import net.sourceforge.stripes.validation.Validate;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.Cfg;

/**
 *
 * @author martijn
 */
@StrictBinding
@MultipartConfig
@UrlBinding("/viewer/api/media/{filename}")
public class MediaActionBean implements ActionBean {

    private ActionBeanContext context;

    @Validate
    private String filename;

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    @DefaultHandler
    public Resolution download() throws Exception {

        // First security check: No path breaker like /../ in filename
        if (filename.contains("..")) {
            return new ErrorMessageResolution(HttpServletResponse.SC_NOT_FOUND, "Document '" + filename + "' niet gevonden");
        }

        String mediaPath = Cfg.getSetting("media");
        File mediaPathDir = new File(mediaPath);
        File f = new File(mediaPath, filename);

        // second security check: resulting path parent file must be the foto directory,
        // not another directory using path breakers like /../ etc.
        if (!f.getParentFile().equals(mediaPathDir)) {
            return new ErrorMessageResolution(HttpServletResponse.SC_BAD_REQUEST, "Filename contains path breaker: " + filename);
        }

        if (!f.exists() || !f.canRead()) {
            return new ErrorMessageResolution(HttpServletResponse.SC_NOT_FOUND, "Document '" + filename + "' niet gevonden");
        }

        String mimeType = Files.probeContentType(f.toPath());
        return new StreamingResolution(mimeType, new FileInputStream(f));
    }

}
