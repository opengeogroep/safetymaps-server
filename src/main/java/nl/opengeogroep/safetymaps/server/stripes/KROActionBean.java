package nl.opengeogroep.safetymaps.server.stripes;

import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_ADMIN;
import static nl.opengeogroep.safetymaps.server.db.JSONUtils.rowToJson;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import net.sourceforge.stripes.action.*;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import nl.opengeogroep.safetymaps.server.db.DB;

/**
 * @author bartv
 */
@UrlBinding("/viewer/api/kro/{path}")
public class KROActionBean implements ActionBean {
    private static final Log log = LogFactory.getLog(SafetyConnectProxyActionBean.class);
    private ActionBeanContext context;
    private String bagId;
    private String address;

    static final String ROLE = "kro";
    static final String ADDRESS_DELIM = "|";
    static final String VIEW_OBJECTINFO = "oovkro.object_info";
    static final String TABLE_OBJECTTYPES = "oovkro.objecttypering_type";
    static final String COLUMN_TYPECODE = "code";
    static final String COLUMN_TYPESCORE = "risico_score";
    static final String COLUMN_TYPEDESCRIPTION = "omschrijving";
    static final String COLUMN_BAGVBID = "bagvboid";
    static final String COLUMN_BAGPANDID = "bagpandid";
    static final String COLUMN_STRAAT = "straatnaam";
    static final String COLUMN_HUISNR = "huisnr";
    static final String COLUMN_HUISLET = "huisletter";
    static final String COLUMN_HUISTOEV = "huistoevg";
    static final String COLUMN_PLAATS = "plaatsnaam";
    static final String COLUMN_OBJECTTYPERING = "adres_objecttypering";

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }
    
    public String getBagId() {
        return bagId;
    }

    public void setBagId(String bagId) {
        this.bagId = bagId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @DefaultHandler
    public Resolution kro() throws Exception {
        if(isNotAuthorized()) {
            return new ErrorMessageResolution(HttpServletResponse.SC_FORBIDDEN, "Gebruiker heeft geen toegang tot kro");
        }
        addCORSHeaders();

        JSONArray response = new JSONArray();
        List<Map<String, Object>> rows = getKroFromDb();
        for (Map<String, Object> row : rows) {
            JSONObject kroFromDb = rowToJson(row, false, false);
            kroFromDb.put("adres_objecttypering_ordered", getFilteredObjectTypesOrderedByScore((String)row.get(COLUMN_OBJECTTYPERING)));
            response.put(kroFromDb);
        }

        return new StreamingResolution("application/json", response.toString());
    }

    public Resolution detailed() throws Exception {
        if(isNotAuthorized()) {
            return new ErrorMessageResolution(HttpServletResponse.SC_FORBIDDEN, "Gebruiker heeft geen toegang tot kro");
        }
        addCORSHeaders();

        JSONArray response = new JSONArray();
        return new StreamingResolution("application/json", response.toString());
    }

    private Boolean isNotAuthorized() {
        return !context.getRequest().isUserInRole(ROLE) && !context.getRequest().isUserInRole(ROLE_ADMIN);
    } 

    private void addCORSHeaders() throws NamingException, SQLException {
        String allowedOrigins = Cfg.getSetting("cors_allowed_origins");

        context.getResponse().addHeader("Access-Control-Allow-Origin", allowedOrigins);
        context.getResponse().addHeader("Access-Control-Allow-Credentials", "true");
    }

    private List<Map<String, Object>> getKroFromDb() throws NamingException, SQLException {
        List<Map<String, Object>> rows;
        QueryRunner qr = DB.kroQr();
        String sql = "select * from " + VIEW_OBJECTINFO + " where ";
        if (useBagId()) {
            sql += COLUMN_BAGVBID + "=?";
            rows = qr.query(sql, new MapListHandler(), this.bagId);
        } else {
            String[] address = splitAddress();
            sql += COLUMN_STRAAT + "=? and " + COLUMN_HUISNR + "=? and " + COLUMN_HUISLET + "=? and " + COLUMN_HUISTOEV + "=? and " + COLUMN_PLAATS + "=?";
            rows = qr.query(sql, new MapListHandler(), address[0], address[1], address[2], address[3], address[4]);
        }
        return rows;
    }

    private List<Map<String, Object>> getObjectTypesOrderedPerScoreFromDb() throws NamingException, SQLException {
        QueryRunner qr = DB.kroQr();
        return qr.query("select " + COLUMN_TYPECODE + ", " + COLUMN_TYPEDESCRIPTION + " from " + TABLE_OBJECTTYPES +  
            " order by " + COLUMN_TYPESCORE + " desc, " + COLUMN_TYPEDESCRIPTION + " asc", new MapListHandler());
    }

    private List<String> getFilteredObjectTypesOrderedByScore(String objectTypesDelimited) throws Exception {
        List<String> objectTypes = new ArrayList<String>();
        List<Map<String, Object>> rows = getObjectTypesOrderedPerScoreFromDb();
        for (Map<String, Object> row : rows) {
            if (objectTypesDelimited.contains((String)row.get(COLUMN_TYPECODE))) {
                objectTypes.add((String)row.get(COLUMN_TYPEDESCRIPTION));
            }
        }
        return objectTypes;
    }

    private List<Map<String, Object>> getKroDetailsFromDb() throws NamingException, SQLException {
        QueryRunner qr = DB.kroQr();
        List<Map<String, Object>> rows = qr.query("", new MapListHandler());
        return rows;
    }

    private Boolean useBagId() {
        return (this.bagId != null && this.bagId != "");
    }

    private String[] splitAddress() {
        return this.address.split("\\" + ADDRESS_DELIM, 5);
    }
}