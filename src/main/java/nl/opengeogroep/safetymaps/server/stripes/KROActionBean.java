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
    private String bagVboId;
    private String bagPandId;
    private String address;

    static final String ROLE = "kro";
    static final String ADDRESS_DELIM = "|";
    static final String VIEW_OBJECT_ADDRESS_INFO = "oovkro.object_address_info";
    static final String VIEW_OBJECTINFO = "oovkro.object_info";
    static final String TABLE_OBJECTTYPES = "oovkro.objecttypering_type";
    static final String TABLE_AANZIEN = "oovkro.aanzien";
    static final String TABLE_GEBRUIK = "oovkro.gebruik";
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
    static final String COLUMN_GEBRUIK_TYPERING = "objecttypering_gebruik";

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }
    
    public String getBagVboId() {
        return bagVboId;
    }

    public void setBagVboId(String bagVboId) {
        this.bagVboId = bagVboId;
    }

    public String getBagPandId() {
        return bagPandId;
    }

    public void setBagPandId(String bagPandId) {
        this.bagPandId = bagPandId;
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

    public Resolution addresses() throws Exception {
        if(isNotAuthorized()) {
            return new ErrorMessageResolution(HttpServletResponse.SC_FORBIDDEN, "Gebruiker heeft geen toegang tot kro");
        }
        addCORSHeaders();

        List<Map<String, Object>> rows;
        List<String> orderedTypes= new ArrayList<String>();
        rows = getObjectTypesOrderedPerScoreFromDb();
        for (Map<String, Object> row : rows) {
            orderedTypes.add((String)row.get(COLUMN_TYPECODE));
        }

        JSONArray response = new JSONArray();
        rows = getKroAddressesFromDb();
        for (Map<String, Object> row : rows) {
            JSONObject kroFromDb = rowToJson(row, false, false);
            response.put(kroFromDb);
        }

        return new StreamingResolution("application/json", response.toString());
    }

    private Boolean isNotAuthorized() {
        return !context.getRequest().isUserInRole(ROLE) && !context.getRequest().isUserInRole(ROLE_ADMIN);
    } 

    private void addCORSHeaders() throws Exception {
        String allowedOrigins = Cfg.getSetting("cors_allowed_origins");

        context.getResponse().addHeader("Access-Control-Allow-Origin", allowedOrigins);
        context.getResponse().addHeader("Access-Control-Allow-Credentials", "true");
    }

    private List<Map<String, Object>> getKroFromDb() throws NamingException, SQLException {
        QueryRunner qr = DB.kroQr();
        String sql = "select * from " + VIEW_OBJECTINFO + " where ";
        Object[] qparams;
        if (useBagId()) {
            sql += COLUMN_BAGVBID + "=?";
            qparams = new Object[] {
                this.bagVboId
            };
        } else {
            String[] address = splitAddress();
            sql += COLUMN_STRAAT + "=? and " + COLUMN_HUISNR + "=? and " + COLUMN_HUISLET + "=? and " + COLUMN_HUISTOEV + "=? and " + COLUMN_PLAATS + "=?";
            qparams = new Object[] {
                address[0], Integer.parseInt(address[1]), address[2], address[3], address[4]
            };
        }
        List<Map<String, Object>> rows = qr.query(sql, new MapListHandler(), qparams);
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

    private List<Map<String, Object>> getKroAddressesFromDb() throws NamingException, SQLException {
        QueryRunner qr = DB.kroQr();
        String sql = "select * from " + VIEW_OBJECT_ADDRESS_INFO + " where ";
        Object[] qparams;

        sql += COLUMN_BAGPANDID + "=?";
        qparams = new Object[] {
            this.bagPandId
        };

        List<Map<String, Object>> rows = qr.query(sql, new MapListHandler(), qparams);
        return rows;
    }

    private Boolean useBagId() {
        return (this.bagVboId != null && this.bagVboId != "");
    }

    private String[] splitAddress() {
        return this.address.split("\\" + ADDRESS_DELIM, 5);
    }
}
