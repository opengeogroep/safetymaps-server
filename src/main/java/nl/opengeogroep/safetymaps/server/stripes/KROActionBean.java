package nl.opengeogroep.safetymaps.server.stripes;

import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_ADMIN;
import static nl.opengeogroep.safetymaps.server.db.JSONUtils.rowToJson;

import org.apache.commons.dbutils.handlers.ScalarHandler;

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
    static final String DEFAULT_DELIM = "|";
    static final String OBJECTTYPEPERADRESS_DELIM = ";;";
    static final String OBJECTTYPEISCOMPANYNAME_DELIM = "**";
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
    static final String COLUMN_OBJECTTYPERING = "pand_objecttypering";
    static final String COLUMN_ADDRESS_OBJECTTYPERING = "adres_objecttypering";
    static final String COLUMN_AANZIEN_OBJECTTYPERING = "aanzien_objecttypering";
    static final String COLUMN_BEDRIJFSNAAM = "adres_bedrijfsnaam";

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
            
            String delimitedBagPandTypes = "";
            String addressObjectTypes = (String)row.get(COLUMN_ADDRESS_OBJECTTYPERING);
            String aanzienObjectTypes = (String)row.get(COLUMN_AANZIEN_OBJECTTYPERING);
            String combinedObjectTypes = (addressObjectTypes == null ||  addressObjectTypes.length() == 0 ? "" : addressObjectTypes) + 
                OBJECTTYPEPERADRESS_DELIM +
                (aanzienObjectTypes == null ||  aanzienObjectTypes.length() == 0 ? "" : aanzienObjectTypes); 

            List<Map<String, Object>> bagPandObjectTypes = getObjectTypesForBagPandId((String)row.get(COLUMN_BAGPANDID));
            for (Map<String, Object> bagPandType : bagPandObjectTypes) {
                delimitedBagPandTypes += OBJECTTYPEPERADRESS_DELIM;
                delimitedBagPandTypes += (String)bagPandType.get(COLUMN_OBJECTTYPERING);
            }
            List<String> orderedObjectTypes = getAndCountObjectTypesOrderedByScore(delimitedBagPandTypes, true);
            List<String> orderedAddressObjectTypes = getAndCountObjectTypesOrderedByScore(combinedObjectTypes, false);

            if (orderedObjectTypes.size() > 0) {
                kroFromDb.put("pand_objecttypering_ordered", orderedObjectTypes);
            }

            if (orderedAddressObjectTypes.size() > 0) {
                kroFromDb.put("address_objecttypering_ordered", orderedAddressObjectTypes);
            } else {
                String text = (String)row.get(COLUMN_BEDRIJFSNAAM);
                if (text == null || text.length() == 0) {
                    text = "Onbekend";
                }
                orderedObjectTypes = new ArrayList<String>();
                orderedObjectTypes.add(text);
                kroFromDb.put("address_objecttypering_ordered", orderedObjectTypes);
            }

            
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
        List<String> orderedTypes = new ArrayList<String>();
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

    public Resolution pand() throws Exception {
        if(isNotAuthorized()) {
            return new ErrorMessageResolution(HttpServletResponse.SC_FORBIDDEN, "Gebruiker heeft geen toegang tot kro");
        }
        addCORSHeaders();

        QueryRunner qr = DB.bagQr();
        String geovlak = qr.query("select geovlak from bag_actueel.pandactueelbestaand_filter where identificatie=?", new ScalarHandler<String>(), getBagPandId());
        
        return new StreamingResolution("application/json", geovlak);
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

    private List<String> getAndCountObjectTypesOrderedByScore(String objectTypesDelimited, Boolean showCount) throws Exception {
        List<String> objectTypes = new ArrayList<String>();

        if (objectTypesDelimited == null) {
            return objectTypes;
        }

        String[] objectTypesPerAddress = splitObjectTypesPerAddress(objectTypesDelimited);
        List<Map<String, Object>> rows = getObjectTypesOrderedPerScoreFromDb();
        for (Map<String, Object> row : rows) {
            String rowObjectType = (String)row.get(COLUMN_TYPECODE);
            if (objectTypesDelimited.contains(rowObjectType)) {
                int count = getItemsFromStringArrayContainingText(objectTypesPerAddress, rowObjectType).size();
                String countText = showCount ? " (" + count + ")" : "";
                objectTypes.add((String)row.get(COLUMN_TYPEDESCRIPTION) + countText);
            }
        }
        return objectTypes;
    }

    private List<Map<String, Object>> getKroAddressesFromDb() throws NamingException, SQLException {
        QueryRunner qr = DB.kroQr();
        String sql = "select * from " + VIEW_OBJECTINFO + " where ";
        Object[] qparams;

        sql += COLUMN_BAGPANDID + "=?";
        qparams = new Object[] {
            this.bagPandId
        };

        List<Map<String, Object>> rows = qr.query(sql, new MapListHandler(), qparams);
        return rows;
    }

    private List<Map<String, Object>> getObjectTypesForBagPandId(String bagPandId) throws Exception {
        QueryRunner qr = DB.kroQr();
        String sql = 
            "select case when adres_objecttypering is not null or aanzien_objecttypering is not null " +
                "then concat(adres_objecttypering, '||', aanzien_objecttypering::text) " +
                "else null::text " +
                "end as " + COLUMN_OBJECTTYPERING + " " +
            "from " + VIEW_OBJECTINFO + " " +
            "where " + COLUMN_BAGPANDID + " = ? " +
            "group by adres, adres_objecttypering, aanzien_objecttypering";
        Object[] qparams;

        qparams = new Object[] {
            bagPandId
        };

        List<Map<String, Object>> rows = qr.query(sql, new MapListHandler(), qparams);
        return rows;
    }

    private Boolean useBagId() {
        return (this.bagVboId != null && this.bagVboId != "");
    }

    private String[] splitAddress() {
        return this.address.split("\\" + DEFAULT_DELIM, 5);
    }

    private String[] splitObjectTypesPerAddress(String objectTypesDelimitedPerAddress) {
        return objectTypesDelimitedPerAddress.split("\\" + OBJECTTYPEPERADRESS_DELIM);
    }

    private List<String> getItemsFromStringArrayContainingText(String[] array, String text) {
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < array.length; i++) {
            if (array[i].contains(text)) {
                result.add(array[i]);
            }
        }
        return result;
    }
}
