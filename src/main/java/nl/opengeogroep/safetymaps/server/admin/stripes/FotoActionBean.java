/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.naming.NamingException;
import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.DontValidate;
import net.sourceforge.stripes.action.ForwardResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.SimpleMessage;
import nl.opengeogroep.safetymaps.server.db.DB;
import org.apache.commons.dbutils.handlers.MapListHandler;

/**
 *
 * @author martijn
 */
public class FotoActionBean implements ActionBean {

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
        String location = context.getRequest().getParameter("location");
        if (filename != null) {
            try {
                deleteFromFileSystem(location);
                DB.qr().update("delete from wfs."+TABLE+" where filename ='" + filename + "'");
                getContext().getMessages().add(new SimpleMessage("Foto verwijderd."));
            } catch (Exception e) {
                e.printStackTrace();
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
        String location = context.getRequest().getParameter("location");
        System.out.println("downloading: " + location);
        File file = new File(location);
        Path source = Paths.get(location);
        context.getResponse().setBufferSize(DEFAULT_BUFFER_SIZE);
        context.getResponse().setContentType(Files.probeContentType(source));
        context.getResponse().setHeader("Content-Length", String.valueOf(file.length()));
        context.getResponse().addHeader("content-disposition", "attachment; filename=" + filename);
        
        FileInputStream fis = null;
        BufferedInputStream input = null;
        BufferedOutputStream output = null;

        try {
            fis = new FileInputStream(file);
            input = new BufferedInputStream(fis, DEFAULT_BUFFER_SIZE);
            output = new BufferedOutputStream(context.getResponse().getOutputStream(), DEFAULT_BUFFER_SIZE);

            
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int length;
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
        } finally {
            
            output.close();
            input.close();
        }
        return null;
    }
    
    @DontValidate
    public byte[] getImage(String fileName) {
        byte[] result = null;
        String fileLocation = fileName;
        File f = new File(fileLocation);
        result = new byte[(int) f.length()];
        try {
            FileInputStream in = new FileInputStream(fileLocation);
            in.read(result);
        } catch (Exception ex) {
            System.out.println("GET IMAGE PROBLEM :: " + ex);
            ex.printStackTrace();
        }
        return result;
    }
    
    public void deleteFromFileSystem(String path) {
        try {

            File file = new File(path);

            if (file.delete()) {
                System.out.println(file.getName() + " is deleted!");
            } else {
                System.out.println("Delete operation failed.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
