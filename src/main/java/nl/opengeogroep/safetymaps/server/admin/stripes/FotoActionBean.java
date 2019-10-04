/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.awt.Image;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.DontValidate;
import net.sourceforge.stripes.action.ForwardResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.SimpleMessage;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.UrlBinding;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import nl.opengeogroep.safetymaps.server.db.DB;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author martijn
 */
@UrlBinding("/admin/action/foto")
public class FotoActionBean implements ActionBean {
    
    private static final Log log = LogFactory.getLog(FotoActionBean.class);
    
    private ActionBeanContext context;

    private List<Map<String, Object>> fotos = new ArrayList();
    
    private static final String JSP = "/admin/fotomanager.jsp";
    
    private static final String TABLE = "\"FotoFunctie\"";
    
    private static final int DEFAULT_BUFFER_SIZE = 10240;
    
    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public List<Map<String, Object>> getFotos() {
        return fotos;
    }

    public void setFotos(List<Map<String, Object>> fotos) {
        this.fotos = fotos;
    }

    @DefaultHandler
    public Resolution list() throws NamingException, SQLException {
        fotos = DB.qr().query("select * from wfs."+TABLE+" order by date", new MapListHandler());
        return new ForwardResolution(JSP);
    }

    @DontValidate
    public Resolution delete() throws Exception {
        String filename = context.getRequest().getParameter("filename");
        if (filename != null) {
            try {
                deleteFromFileSystem(filename);
                DB.qr().update("delete from wfs." + TABLE + " where filename =?", filename);
                getContext().getMessages().add(new SimpleMessage("Foto verwijderd."));
            } catch (Exception e) {
                log.error(e);
            }
        } else {
            getContext().getMessages().add(new SimpleMessage("Geen bestandsnaam om te verwijderen!"));
        }
        list();
        return new ForwardResolution(JSP);
    }

    @DontValidate
    public Resolution downloadFoto() throws Exception {
        String filename = context.getRequest().getParameter("filename");
        String location = Cfg.getSetting("fotofunctie");
        File f = new File(location + File.separator + filename);

        if(!f.exists() || !f.canRead()) {
            return new ErrorMessageResolution(HttpServletResponse.SC_NOT_FOUND, "Foto '" + filename + "' niet gevonden");
        }

        String mimeType = Files.probeContentType(f.toPath());
        return new StreamingResolution(mimeType, new FileInputStream(f));
    }
    
    public void deleteFromFileSystem(String filename) throws NamingException, SQLException, Exception {
        String path = Cfg.getSetting("fotofunctie") + filename;
        try {
            File file = new File(path);
            Image image = ImageIO.read(file);
            if (image != null && file.exists()) {
                if (file.delete()) {
                    log.info(file.getName() + " is deleted!");                    
                } else {
                    log.debug("Delete operation failed.");
                    throw new Exception("Delete operation failed");
                }
            } else {
                log.debug("The file" + path + "could not be deleted , it is not an image or does nor exist");
                throw new Exception("The file" + path + "could not be deleted , it is not an image or does nor exist");
            }
        } catch (IOException e) {
            log.error(e);
        }
    }
}
