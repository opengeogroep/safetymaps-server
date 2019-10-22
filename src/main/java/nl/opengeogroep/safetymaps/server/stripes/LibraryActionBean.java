package nl.opengeogroep.safetymaps.server.stripes;

import java.io.File;
import java.io.FileInputStream;
import java.net.URLDecoder;
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
import nl.opengeogroep.safetymaps.server.db.DB;
import org.apache.commons.dbutils.handlers.ScalarHandler;

/**
 *
 * @author martijn
 */
@StrictBinding
@MultipartConfig
@UrlBinding("/viewer/api/media/bibliotheek/{filename}")
public class LibraryActionBean implements ActionBean{
    
    private ActionBeanContext context;
    
    private final String TABLE = "\"Bibliotheek\"";
    
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
    public Resolution download() throws Exception{
        
        // First pathname security check: must exist in db
        boolean exists = DB.qr().query("select 1 from wfs." + TABLE + " where \"Documentnaam\" = ?", new ScalarHandler<>(), filename) != null;
        
        // Second security check: No path breaker like /../ in filename
        if(!exists || filename.contains("..")) {
            return new ErrorMessageResolution(HttpServletResponse.SC_NOT_FOUND, "Document '" + filename + "' niet gevonden");
        }
        
        
        String libraryPath = Cfg.getSetting("library");
        File libraryPathDir = new File(libraryPath);
        File f = new File(libraryPath,filename);
        
        // Third security check: resulting path parent file must be the foto directory,
        // not another directory using path breakers like /../ etc.
        if(!f.getParentFile().equals(libraryPathDir)) {
            return new ErrorMessageResolution(HttpServletResponse.SC_BAD_REQUEST, "Filename contains path breaker: " + filename);
        }
        
        if(!f.exists() || !f.canRead()) {
            return new ErrorMessageResolution(HttpServletResponse.SC_NOT_FOUND, "Document '" + filename + "' niet gevonden");
        }
        
        String mimeType = Files.probeContentType(f.toPath());
        return new StreamingResolution(mimeType, new FileInputStream(f));
    }
}
