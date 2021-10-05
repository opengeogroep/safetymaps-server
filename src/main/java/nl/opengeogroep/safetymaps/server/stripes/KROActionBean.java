package nl.opengeogroep.safetymaps.server.stripes;

import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_ADMIN;
import static nl.opengeogroep.safetymaps.server.db.JSONUtils.rowToJson;

import org.apache.commons.dbutils.handlers.ScalarHandler;

import java.sql.SQLException;
import java.text.Normalizer;
import java.text.Normalizer.Form;
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
    static final String TABLE_CONFIG = "safetymaps.kro";
    static final String COLUMN_TYPECODE = "code";
    static final String COLUMN_TYPESCORE = "risico_score";
    static final String COLUMN_TYPEDESCRIPTION = "omschrijving_aangepast";
    static final String COLUMN_BAGVBID = "bagvboid";
    static final String COLUMN_BAGPANDID = "bagpandid";
    static final String COLUMN_STRAAT = "straatnaam";
    static final String COLUMN_HUISNR = "huisnr";
    static final String COLUMN_HUISLET = "huisletter";
    static final String COLUMN_HUISTOEV = "huistoevg";
    static final String COLUMN_PLAATS = "plaatsnaam";
    static final String COLUMN_PC= "postcode";
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

    public Resolution config() throws Exception {
        if(isNotAuthorized()) {
            return new ErrorMessageResolution(HttpServletResponse.SC_FORBIDDEN, "Gebruiker heeft geen toegang tot kro");
        }

        JSONArray response = new JSONArray();
        List<Map<String, Object>> rows;
        rows = getConfigFromDb();
        for (Map<String, Object> row : rows) {
            JSONObject configFromDb = rowToJson(row, false, false);
            response.put(configFromDb);
        }

        return new StreamingResolution("application/json", response.toString());
    }

    public Resolution addresses() throws Exception {
        if(isNotAuthorized()) {
            return new ErrorMessageResolution(HttpServletResponse.SC_FORBIDDEN, "Gebruiker heeft geen toegang tot kro");
        }
        
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

        QueryRunner qr = DB.bagQr();
        List<Map<String, Object>> result = qr.query("select st_astext(st_force2d(geovlak)) as pandgeo from bag_actueel.pandactueelbestaand_filter where identificatie=?", new MapListHandler(), getBagPandId());
        
        JSONArray response = new JSONArray();
        for (Map<String, Object> row : result) {
            JSONObject pandGeo = rowToJson(row, false, false);
            response.put(pandGeo);
        }

        return new StreamingResolution("application/json", response.toString());
    }

    private Boolean isNotAuthorized() {
        return false;
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
            sql += COLUMN_HUISNR + "=? and " + COLUMN_HUISLET + "=? and " + COLUMN_HUISTOEV + "=? and (" + COLUMN_PC + "=? or (" + COLUMN_PLAATS + "=? and " + COLUMN_STRAAT + "=?))";
            qparams = new Object[] {
                Integer.parseInt(address[1]), address[2], address[3], address[5], address[4], address[0]
            };
        }
        List<Map<String, Object>> rows = qr.query(sql, new MapListHandler(), qparams);
        return rows;
    }

    private List<Map<String, Object>> getObjectTypesOrderedPerScoreFromDb() throws NamingException, SQLException {
        QueryRunner qr = DB.kroQr();
        return qr.query("select code, omschrijving_aangepast, risico_score from oovkro.objecttypering_type ot " +
            "union select 'woonfunctie' as code, 'woonfunctie' as omschrijving_aangepast, -1 as risico_score " +
            "union select 'bijeenkomstfunctie' as code, 'bijeenkomstfunctie' as omschrijving_aangepast, -1 as risico_score " +
            "union select 'celfunctie' as code, 'celfunctie' as omschrijving_aangepast, -1 as risico_score " +
            "union select 'gezondheidsfunctie' as code, 'gezondheidsfunctie' as omschrijving_aangepast, -1 as risico_score " +
            "union select 'industriefunctie' as code, 'industriefunctie' as omschrijving_aangepast, -1 as risico_score " +
            "union select 'kantoorfunctie' as code, 'kantoorfunctie' as omschrijving_aangepast, -1 as risico_score " +
            "union select 'logiesfunctie' as code, 'logiesfunctie' as omschrijving_aangepast, -1 as risico_score " +
            "union select 'onderwijsfunctie' as code, 'onderwijsfunctie' as omschrijving_aangepast, -1 as risico_score " +
            "union select 'sportfunctie' as code, 'sportfunctie' as omschrijving_aangepast, -1 as risico_score " +
            "union select 'winkelfunctie' as code, 'winkelfunctie' as omschrijving_aangepast, -1 as risico_score " +
            "order by risico_score desc, omschrijving_aangepast asc;", new MapListHandler());
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

    private List<Map<String, Object>> getConfigFromDb() throws NamingException, SQLException {
        QueryRunner qr = DB.qr();
        String sql = "select * from " + TABLE_CONFIG + "";
        List<Map<String, Object>> rows = qr.query(sql, new MapListHandler());
        return rows;
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
        return this.address.split("\\" + DEFAULT_DELIM, 6);
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
