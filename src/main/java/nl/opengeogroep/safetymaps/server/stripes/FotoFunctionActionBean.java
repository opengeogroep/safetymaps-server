/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.server.stripes;

import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.Validate;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import nl.opengeogroep.safetymaps.server.db.DB;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import static nl.opengeogroep.safetymaps.server.db.JSONUtils.rowToJson;

/**
 *
 * @author martijn
 */
@StrictBinding
@MultipartConfig
@UrlBinding("/viewer/api/foto")
public class FotoFunctionActionBean implements ActionBean {

    private static final Log log = LogFactory.getLog(FotoFunctionActionBean.class);
    private static final String TABLE = "\"FotoFunctie\"";
    private ActionBeanContext context;
    private String PATH = "";

    @Validate
    private FileBean picture;

    @Validate
    private String fileName;

    @Validate
    private String extraInfo;

    @Validate
    private String voertuigNummer;

    @Validate
    private String incidentNummer;

    @Validate
    private String type;

    public String getVoertuigNummer() {
        return voertuigNummer;
    }

    public void setVoertuigNummer(String voertuigNummer) {
        this.voertuigNummer = voertuigNummer;
    }

    public String getIncidentNummer() {
        return incidentNummer;
    }

    public void setIncidentNummer(String incidentNummer) {
        this.incidentNummer = incidentNummer;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getExtraInfo() {
        return extraInfo;
    }

    public void setExtraInfo(String extraInfo) {
        this.extraInfo = extraInfo;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    public FileBean getPicture() {
        return picture;
    }

    public void setPicture(FileBean picture) {
        this.picture = picture;
    }

    @DefaultHandler
    public Resolution foto() throws IOException, ServletException {
        JSONObject response = new JSONObject();
        try {
            PATH = Cfg.getSetting("fotofunctie");
            if(PATH == null) {
                throw new IllegalArgumentException("Fotofunctie serverpad niet geconfigureerd");
            }

            response.put("result", false);
            if (extraInfo == null) {
                extraInfo = "";
            }

            if (incidentNummer == null) {
                incidentNummer = "N.V.T.";
            }
            
            fileName = fileName.replace('/','_');
            final File file = new File(PATH + File.separator + fileName);
            picture.save(file);
            insertIntoDb();
            response.put("message", "Foto is opgeslagen met bestandsnaam: " + fileName);
            response.put("result", true);
        } catch (Exception e) {
            response.put("message", "Error met fout " + e.getMessage());
        }
        return new StreamingResolution("application/json", response.toString());
    }

    public Resolution download() throws Exception {
        // First pathname security check: must exist in db
        boolean exists = DB.qr().query("select 1 from wfs." + TABLE + " where filename = ?", new ScalarHandler<>(), fileName) != null;

        // Second security check: No path breaker like /../ in filename
        if(!exists || fileName.contains("..")) {
            return new ErrorMessageResolution(HttpServletResponse.SC_NOT_FOUND, "Foto '" + fileName + "' niet gevonden");
        }

        String fotoPath = Cfg.getSetting("fotofunctie");
        File fotoPathDir = new File(fotoPath);
        File f = new File(fotoPath + File.separator + fileName);

        // Third security check: resulting path parent file must be the foto directory,
        // not another directory using path breakers like /../ etc.
        if(!f.getParentFile().equals(fotoPathDir)) {
            return new ErrorMessageResolution(HttpServletResponse.SC_BAD_REQUEST, "Filename contains path breaker: " + fileName);
        }

        if(!f.exists() || !f.canRead()) {
            return new ErrorMessageResolution(HttpServletResponse.SC_NOT_FOUND, "Foto '" + fileName + "' niet gevonden");
        }

        String mimeType = Files.probeContentType(f.toPath());
        return new StreamingResolution(mimeType, new FileInputStream(f));
    }

    public Resolution fotoForIncident() throws Exception {      
        JSONArray response = new JSONArray();

        List<Map<String, Object>> rows = getFromDb();

        for (Map<String, Object> row : rows) {
            response.put(rowToJson(row, false, false));
        }

        return new StreamingResolution("application/json", response.toString());
    }

    public void insertIntoDb() throws Exception {
        Calendar calendar = Calendar.getInstance();
        java.sql.Date date = new java.sql.Date(calendar.getTime().getTime());
        Object[] qparams = new Object[] {
            fileName,
            type,
            voertuigNummer,
            incidentNummer,
            date,
            extraInfo
        };
        try {
            QueryRunner qr = DB.qr();
            qr.insert("insert into wfs." + TABLE + " (filename, datatype, voertuig_nummer, incident_nummer, date, omschrijving) values(?,?,?,?,?,?)", new MapListHandler(), qparams);
        } catch (Exception e) {
            throw e;
        }
    }

    public List<Map<String, Object>> getFromDb() throws Exception {
        QueryRunner qr = DB.qr();

        List<Map<String, Object>> rows = qr.query("SELECT \"filename\", \"omschrijving\" from wfs."+TABLE+" where incident_nummer =?", new MapListHandler(),incidentNummer);

        return rows;
    }
}
