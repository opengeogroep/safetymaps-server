package nl.opengeogroep.safetymaps.server.stripes;

import java.io.OutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.*;
import nl.opengeogroep.safetymaps.server.db.DB;
import nl.opengeogroep.safetymaps.server.db.JSONUtils;
import static nl.opengeogroep.safetymaps.server.db.JSONUtils.rowToJson;
import static nl.opengeogroep.safetymaps.server.db.JsonExceptionUtils.logExceptionAndReturnJSONObject;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.ArrayListHandler;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.dbutils.handlers.KeyedHandler;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/viewer/api/vrh/{path}")
public class VrhActionBean implements ActionBean {
    private ActionBeanContext context;

    private static final Log log = LogFactory.getLog(VrhActionBean.class);

    private static final String MAP_DBKS = "dbks.json";
    private static final String MAP_WATERONGEVALLEN = "waterongevallen.json";
    private static final String MAP_EVENEMENTEN = "evenementen.json";
    private static final String TYPE_DBK = "dbk";
    private static final String TYPE_WATERONGEVALLEN = "waterongevallenkaart";
    private static final String TYPE_EVENEMENT = "evenement";

    private static JSONArray dbksCache;
    private static Long dbksCacheLastModified;
    private static JSONArray dbksCacheNewSchema;
    private static Long dbksCacheNewSchemaLastModified;
    private static Long lastImportTime;
    private static Long lastImportTimeCheckedAt;

    /* Only check for a new import every 60 seconds */
    private static final int LAST_IMPORT_TIME_CACHE_MAX_AGE_MILLIS = 60000;

    @Validate
    private String path;

    @Validate
    private int indent = 0;

    @Validate
    private boolean newDbSchema = false;

    @Validate
    private boolean noCache = false;

    // <editor-fold defaultstate="collapsed" desc="getters and setters">
    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getIndent() {
        return indent;
    }

    public void setIndent(int indent) {
        this.indent = indent;
    }

    public boolean isNewDbSchema() {
        return newDbSchema;
    }

    public void setNewDbSchema(boolean newDbSchema) {
        this.newDbSchema = newDbSchema;
    }

    public boolean isNoCache() {
        return noCache;
    }

    public void setNoCache(boolean noCache) {
        this.noCache = noCache;
    }
    // </editor-fold>

    private static JSONArray rowsToJSONArray(List<Map<String,Object>> rows) throws Exception {
        JSONArray a = new JSONArray();
        for(Map<String,Object> row: rows) {
            a.put(JSONUtils.rowToJson(row, true, true));
        }
        return a;
    }

    private Resolution jsonStreamingResolution(final JSONObject j, final Long lastModified) {
        return new Resolution() {
            @Override
            public void execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
                String encoding = "UTF-8";
                response.setCharacterEncoding(encoding);
                response.setContentType("application/json");
                if(lastModified != null) {
                    response.addDateHeader("Last-Modified", lastModified * 1000);
                }

                OutputStream out;
                String acceptEncoding = request.getHeader("Accept-Encoding");
                if(acceptEncoding != null && acceptEncoding.contains("gzip")) {
                    response.setHeader("Content-Encoding", "gzip");
                    out = new GZIPOutputStream(response.getOutputStream(), true);
                } else {
                    out = response.getOutputStream();
                }
                IOUtils.copy(new StringReader(j.toString(indent)), out, encoding);
                out.flush();
                out.close();
            }
        };
    }

    private String getIdFromPath(String type) throws Exception {
        Pattern p = Pattern.compile(type + "\\/([\\-0-9p]+)\\.json");
        Matcher m = p.matcher(path);

        if(!m.find()) {
            throw new Exception("No " + type + " id found in path: " + getContext().getRequest().getRequestURI());
        } else {
            return m.group(1);
        }
    }

    public Resolution api() {
        try(Connection c = DB.getConnection()) {
            if(path != null) {
                Object result;

                if(MAP_DBKS.equals(path)) {
                    return dbks(c);
                } else if(path.indexOf(TYPE_DBK + "/") == 0) {
                    if(newDbSchema) {
                        result = dbkJsonNewDbSchema(c, getIdFromPath(TYPE_DBK));
                    } else {
                        result = dbkJson(c, Integer.parseInt(getIdFromPath(TYPE_DBK)));
                    }
                } else if(MAP_WATERONGEVALLEN.equals(path)) {
                    result = waterongevallenJson(c, newDbSchema);
                } else if(path.indexOf(TYPE_WATERONGEVALLEN + "/") == 0) {
                    result = waterongevallenkaartJson(c, Integer.parseInt(getIdFromPath(TYPE_WATERONGEVALLEN)), newDbSchema);
                } else if(MAP_EVENEMENTEN.equals(path)) {
                    result = evenementenJson(c);
                } else if(path.indexOf(TYPE_EVENEMENT + "/") == 0) {
                    result = evenementJson(c, Integer.parseInt(getIdFromPath(TYPE_EVENEMENT)));
                } else {
                    return new ErrorResolution(HttpServletResponse.SC_NOT_FOUND, "Not found: " + getContext().getRequest().getRequestURI());
                }

                final JSONObject r = new JSONObject();
                r.put("success", true);
                r.put("results", result);
                return jsonStreamingResolution(r, null);
            }

            return new ErrorResolution(HttpServletResponse.SC_NOT_FOUND, "Not found: " + getContext().getRequest().getRequestURI());
        } catch(Exception e) {
            return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Error on " + getContext().getRequest().getRequestURI(), e).toString(indent));
        }
    }

    private long getCachedLastImportTime(Connection c) throws Exception {
        long now = System.currentTimeMillis();
        if(lastImportTime == null || now - lastImportTimeCheckedAt > LAST_IMPORT_TIME_CACHE_MAX_AGE_MILLIS) {
            lastImportTime = new QueryRunner().query(c, "select time from vrh.import_metadata limit 1", new ScalarHandler<Timestamp>()).getTime() / 1000;
            lastImportTimeCheckedAt = now;
        }
        return lastImportTime;
    }

    private Resolution dbks(Connection c) throws Exception {

        long lastModified = getCachedLastImportTime(c);
        long ifModifiedSince = getContext().getRequest().getDateHeader("If-Modified-Since") / 1000;

        if(ifModifiedSince >= lastModified) {
            return new ErrorResolution(HttpServletResponse.SC_NOT_MODIFIED);
        }

        JSONArray dbks;

        if(newDbSchema) {
            if(noCache || dbksCacheNewSchema == null || lastModified > dbksCacheNewSchemaLastModified) {
                dbksCacheNewSchema = dbksNewDbSchema(c);
                dbksCacheNewSchemaLastModified = lastModified;
            }
            dbks = dbksCacheNewSchema;
        } else {
            if(noCache || dbksCache == null || lastModified > dbksCacheLastModified) {
                dbksCache = dbksJson(c);
                dbksCacheLastModified = lastModified;
            }
            dbks = dbksCache;
        }

        JSONObject r = new JSONObject();
        r.put("success", true);
        r.put("results", dbks);

        return jsonStreamingResolution(r, lastModified);
    }

    public static JSONArray dbksJson(Connection c) throws Exception {
        QueryRunner qr = new QueryRunner();
        JSONArray objects = new JSONArray();

        List<Map<String,Object>> rows = qr.query(c, "select o.id, naam, oms_nummer, straatnaam, huisnummer, huisletter, toevoeging, postcode, plaats, " +
                "st_astext(st_centroid(o.geom)) as pand_centroid, box2d(o.geom)::varchar as extent " +
                "from vrh.dbk_object o " +
                "left join vrh.pand p on (p.dbk_object = o.id) " +
                "where p.hoofd_sub='Hoofdpand' " +
                "and naam is not null " +
                "order by naam", new MapListHandler());

        List<Map<String,Object>> adressen = qr.query(c, "select dbk_object as id, " +
                "        (select array_to_json(array_agg(row_to_json(r.*))) " +
                "         from (select na.postcode as pc, na.woonplaats as pl, na.straatnaam as sn,  " +
                "                array_to_json(array_agg(distinct na.huisnummer || case when na.huisletter <> '' or na.toevoeging <> '' then '|' || coalesce(na.huisletter,'') || '|' || coalesce(na.toevoeging,'') else '' end)) as nrs " +
                "                from vrh.nevendbkadres na " +
                "                where na.dbk_object = t.dbk_object " +
                "                group by na.postcode, na.woonplaats, na.straatnaam) r " +
                "        ) as selectieadressen " +
                "    from (select distinct dbk_object from vrh.nevendbkadres) t", new MapListHandler());

        Map<Object,String> selectieadressenById = new HashMap();
        for(Map<String,Object> row: adressen) {
            selectieadressenById.put(row.get("id"), row.get("selectieadressen").toString());
        }

        Set objectIds = new HashSet();

        for(Map<String,Object> row: rows) {
            JSONObject o = JSONUtils.rowToJson(row, true, true);

            Object id = row.get("id");
            if(objectIds.contains(id)) {
                // Duplicate hoofdpand pand row
                continue;
            }
            objectIds.add(id);

            String selectieadressen = selectieadressenById.get(id);
            if(selectieadressen != null) {
                o.put("selectieadressen", new JSONArray(selectieadressen));
            }
            objects.put(o);
        }
        return objects;
    }

    private static JSONArray dbksNewDbSchema(Connection c) throws Exception {
        QueryRunner qr = new QueryRunner();
        JSONArray objects = new JSONArray();

        List<Map<String,Object>> objectRows = qr.query(c, "select vrh_bag_id as id, naam, oms_nummer, st_astext(geom) as pand_centroid " +
                "from vrh_new.vrh_geo_dbk_bag_object o " +
                "order by naam", new MapListHandler());

        Map<String,Map<String,Object>> objectExtents = (Map<String,Map<String,Object>>)qr.query(c, "select vrh_bag_id, st_extent(geom) as extent from vrh_new.vrh_geo_pand group by vrh_bag_id", new KeyedHandler("vrh_bag_id"));

        final Map<String,Set<String>> objectPandIds = new HashMap();
        final Set<String> allPandIds = new HashSet();

        // Geen negatieve of verkeerd ingevulde niet-numerieke "ids"
        qr.query(c, "select vrh_bag_id, bagpand_id from vrh_new.vrh_geo_pand where bagpand_id ~ '^[0-9]+$'", new ResultSetHandler() {
            @Override
            public Object handle(ResultSet rs) throws SQLException {
                while(rs.next()) {
                    String vrhBagId = rs.getString(1);
                    String pandId = rs.getString(2);
                    Set<String> thisObjectPandIds = objectPandIds.get(vrhBagId);
                    if(thisObjectPandIds == null) {
                        thisObjectPandIds = new HashSet();
                        objectPandIds.put(vrhBagId, thisObjectPandIds);
                    }
                    thisObjectPandIds.add(pandId);
                    allPandIds.add(pandId);
                }
                return null;
            }
        });

        List<Map<String,Object>> pandAdresRows = DB.bagQr().query("select pandid, nummeraanduiding, openbareruimtenaam, huisnummer, huisletter, huisnummertoevoeging, postcode, woonplaatsnaam, st_astext(st_force2d(geopunt)) as geopunt from bag_actueel.adres_full where pandid in (" + StringUtils.repeat("?", ", ", allPandIds.size()) + ")", new MapListHandler(), (Object[])allPandIds.toArray(new String[] {}));
        Map<String,List<Map<String,Object>>> pandIdAdressen = new HashMap();
        for(Map<String,Object> r: pandAdresRows) {
            String pandId = (String)r.get("pandid");
            List<Map<String,Object>> pandAdressen = pandIdAdressen.get(pandId);
            if(pandAdressen == null) {
                pandAdressen = new ArrayList();
                pandIdAdressen.put(pandId, pandAdressen);
            }
            pandAdressen.add(r);
        }

        for(Map<String,Object> row: objectRows) {
            JSONObject o = JSONUtils.rowToJson(row, true, true);
            objects.put(o);

            String vrhBagId = (String)row.get("id");

            Map<String,Object> extent = objectExtents.get(vrhBagId);
            if(extent != null) {
                o.put("extent", extent.get("extent"));
            }

            JSONArray extraAdressenJson = new JSONArray();

            Set<String> pandIds = objectPandIds.get(vrhBagId);
            if(pandIds != null) {
                for(String pandId: pandIds) {
                    List<Map<String,Object>> adressen = pandIdAdressen.get(pandId);
                    if(adressen != null) {
                        for(Map<String,Object> bagAdres: adressen) {
                            String nummeraanduiding = (String)bagAdres.get("nummeraanduiding");
                            if(vrhBagId.equals(nummeraanduiding)) {
                                String hoofdpand = (String)bagAdres.get("pandid");
                                String newId = o.get("id") + "p" + hoofdpand;
                                o.put("id", newId);

                                o.put("straatnaam", bagAdres.get("openbareruimtenaam"));
                                o.put("huisnummer", bagAdres.get("huisnummer"));
                                o.put("huisletter", bagAdres.get("huisletter"));
                                o.put("toevoeging", bagAdres.get("huisnummertoevoeging"));
                                o.put("postcode", bagAdres.get("postcode"));
                                o.put("plaats", bagAdres.get("woonplaatsnaam"));
                            } else {
                                addExtraAdres(extraAdressenJson, bagAdres);
                            }
                        }
                    }
                }
            } else {
                Map<String,Object> adresNietBag = new QueryRunner().query(c, "select straatnaam, huisnummer, huisletter, toevoeging, postcode, woonplaats, adres_loca from vrh_new.vrh_geo_adres_niet_bag where id = ?", new MapHandler(), vrhBagId);

                if(adresNietBag != null) {
                    row.put("locatie", adresNietBag.get("adres_loca"));
                    row.put("straatnaam", adresNietBag.get("straatnaam"));
                    row.put("huisnummer", adresNietBag.get("huisnummer"));
                    row.put("huisletter", adresNietBag.get("huisletter"));
                    row.put("toevoeging", adresNietBag.get("toevoeging"));
                    row.put("postcode", adresNietBag.get("postcode"));
                    row.put("plaats", adresNietBag.get("woonplaats"));
                }
            }

            if(extraAdressenJson.length() > 0) {
                o.put("selectieadressen", extraAdressenJson);
            }
        }

        return objects;
    }

    private static void addExtraAdres(JSONArray extraAdressenJson, Map<String,Object> adres) {
        String sn = (String)adres.get("openbareruimtenaam");
        String pl = (String)adres.get("woonplaatsnaam");
        String pc = (String)adres.get("postcode");
        String extraNr = adres.get("huisnummer") + "";
        if(adres.get("huisletter") != null || adres.get("huisnummertoevoeging") != null ) {
            extraNr += "|" + ObjectUtils.firstNonNull(adres.get("huisletter"), "");
            extraNr += "|" + ObjectUtils.firstNonNull(adres.get("huisnummertoevoeging"), "");
        }
        boolean added = false;
        JSONObject extraAdres = null;
        for(int i = 0; i < extraAdressenJson.length(); i++) {
            JSONObject ea = extraAdressenJson.getJSONObject(i);
            if(sn.equals(ea.get("sn")) && pc.equals(ea.get("pc")) && pl.equals(ea.get("pl"))) {
                extraAdres = ea;
                break;
            }
        }
        if(extraAdres == null) {
            extraAdres = new JSONObject();
            extraAdres.put("pl", pl);
            extraAdres.put("pc", pc);
            extraAdres.put("sn", sn);
            JSONArray nrs = new JSONArray();
            extraAdres.put("nrs", nrs);
            extraAdressenJson.put(extraAdres);
        }
        // Voeg huisnummer|huisletter|huisnummertoevoeving toe aan nrs array
        extraAdres.getJSONArray("nrs").put(extraNr);
    }

    public static JSONObject dbkJsonNewDbSchema(Connection c, String id) throws Exception {

        String hoofdpandId = null;
        String[] idParts = id.split("p");
        if(idParts.length == 2) {
            id = idParts[0];
            hoofdpandId = idParts[1];
        }

        List<String> bagpandIds = new QueryRunner().query(c, "select bagpand_id from vrh_new.vrh_geo_pand where vrh_bag_id = ?", new ColumnListHandler<String>(), id);
        if(hoofdpandId == null && !bagpandIds.isEmpty()) {
            // for vrh_geo_adres_niet_bag - take the first record
            hoofdpandId = bagpandIds.get(0);
        }

        for(int i = 0; i < bagpandIds.size(); i++) {
            bagpandIds.set(i, "'" + bagpandIds.get(i) + "'");
        }
        String bagPandIdsQuery = "in (" + StringUtils.join(bagpandIds.toArray(new String[] {}), ",") + ")";

        List<Map<String,Object>> rows = new QueryRunner().query(c, "select " +
                "    o.*, " +
//                "    (select st_astext(st_collect(sp.geom)) from vrh_new.vrh_geo_pand sp where sp.vrh_bag_id = p.vrh_bag_id) as geometry, " +
//                "    st_astext(p.geom) as geometry, " +

                "    (select row_to_json(r.*) " +
                "    from (select *, st_astext(t.geom) as geometry " +
                "         from vrh_new.vrh_geo_pand t " +
                "         where t.vrh_bag_id = o.vrh_bag_id" +
                "         and bagpand_id = ?" +
                "         limit 1) r " +
                "    ) as hoofdpand, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select bagpand_id, st_astext(t.geom) as geometry " +
                "         from vrh_new.vrh_geo_pand t " +
                "         where t.vrh_bag_id = o.vrh_bag_id" +
                "         and bagpand_id <> ?) r " +
                "    ) as subpanden, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select *, st_astext(t.geom) as geometry " +
                "         from vrh_new.vrh_geo_compartimentering t " +
                "         where t.bagpand_id " + bagPandIdsQuery + ") r " +
                "    ) as compartimentering, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select objectid, symboolcod, symboolhoe, symboolgro, omschrijvi, bijzonderh, st_astext(t.geom) as geometry " +
                "         from vrh_new.vrh_geo_brandweervoorziening t " +
                "         where t.bagpand_id " + bagPandIdsQuery + ") r " +
                "    ) as brandweervoorziening, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select *, st_astext(t.geom) as geometry " +
                "         from vrh_new.vrh_geo_opstelplaats t " +
                "         where t.bagpand_id " + bagPandIdsQuery + ") r " +
                "    ) as opstelplaats, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select objectid, symboolcod, symboolgro, omschrijvi, bijzonderh, symboolhoe, st_astext(t.geom) as geometry " +
                "         from vrh_new.vrh_geo_toegang_pand t " +
                "         where t.bagpand_id " + bagPandIdsQuery + ") r " +
                "    ) as toegang_pand, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select *, st_astext(t.geom) as geometry " +
                "         from vrh_new.vrh_geo_toegang_terrein t " +
                "         where t.bagpand_id " + bagPandIdsQuery + ") r " +
                "    ) as toegang_terrein, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select objectid, symboolcod, symboolgro, bijzonderh, soort_geva, locatie, st_astext(t.geom) as geometry " +
                "         from vrh_new.vrh_geo_gevaren t " +
                "         where t.bagpand_id " + bagPandIdsQuery + ") r " +
                "    ) as gevaren, " +
/*
                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select *, st_astext(t.geom) as geometry " +
                "         from vrh.hellingbaan t " +
                "         where t.dbk_object = o.id) r " +
                "    ) as hellingbaan, " +
*/
                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select *, st_astext(t.geom) as geometry " +
                "         from vrh_new.vrh_geo_gevaarlijke_stoffen t " +
                "         where t.bagpand_id " + bagPandIdsQuery +
                "         order by volgnummer) r " +
                "    ) as gevaarlijke_stoffen, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select objectid, coalesce(type, symboolcod) as type, bijzonderh, opmerkinge, st_astext(t.geom) as geometry " +
                "         from vrh_new.vrh_geo_dbk_lijn t " +
                "         where t.bagpand_id " + bagPandIdsQuery + ") r " +
                "    ) as overige_lijnen, " +
/*
                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select *, st_astext(t.geom) as geometry " +
                "         from vrh.slagboom t " +
                "         where t.dbk_object = o.id) r " +
                "    ) as slagboom, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select *, st_astext(t.geom) as geometry " +
                "         from vrh.aanpijling t " +
                "         where t.dbk_object = o.id) r " +
                "    ) as aanpijling, " +
*/
                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select objectid, tekst, symboolgro, symboolhoe, st_astext(t.geom) as geometry " +
                "         from vrh_new.vrh_geo_tekst t " +
                "         where t.bagpand_id " + bagPandIdsQuery + ") r " +
                "    ) as teksten, " +
/*
                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select *, st_astext(t.geom) as geometry " +
                "         from vrh.aanrijroute t " +
                "         where t.dbk_object = o.id) r " +
                "    ) as aanrijroute " +
*/
                "    (select array_to_json(array_agg(brandinstallaties)) \n" +
                "    from (select brandinstallaties \n" +
                "         from vrh_new.brandinstallaties t \n" +
                "         where t.vrh_bag_id = o.vrh_bag_id) r \n" +
                "    ) as brandinstallaties, " +

                "    (select array_to_json(array_agg(naam_bijlage)) \n" +
                "    from (select naam_bijlage \n" +
                "         from vrh_new.bijlage_voertuigviewer b \n" +
                "         where b.vrh_bag_id = o.vrh_bag_id" +
                "         order by naam_bijlage) r \n" +
                "    ) as media " +

                "from vrh_new.vrh_geo_dbk_bag_object o where o.vrh_bag_id = ?", new MapListHandler(), hoofdpandId, hoofdpandId, id);

        if(rows.isEmpty()) {
            throw new IllegalArgumentException("DBK met ID " + id + " niet gevonden");
        }

        Map<String,Object> bagAdres = DB.bagQr().query("select openbareruimtenaam, huisnummer, huisletter, huisnummertoevoeging, postcode, woonplaatsnaam from bag_actueel.adres where nummeraanduiding = ?", new MapHandler(), id);

        Map<String,Object> row = rows.get(0);
        row.remove("geom");

        if(bagAdres != null) {
            row.put("straatnaam", bagAdres.get("openbareruimtenaam"));
            row.put("huisnummer", bagAdres.get("huisnummer"));
            row.put("huisletter", bagAdres.get("huisletter"));
            row.put("toevoeging", bagAdres.get("huisnummertoevoeging"));
            row.put("postcode", bagAdres.get("postcode"));
            row.put("plaats", bagAdres.get("woonplaatsnaam"));
            row.put("bagPunt", bagAdres.get("geopunt"));
        } else {
            Map<String,Object> adresNietBag = new QueryRunner().query(c, "select straatnaam, huisnummer, huisletter, toevoeging, postcode, woonplaats, adres_loca from vrh_new.vrh_geo_adres_niet_bag where id = ?", new MapHandler(), id);

            if(adresNietBag != null) {
                row.put("locatie", adresNietBag.get("adres_loca"));
                row.put("straatnaam", adresNietBag.get("straatnaam"));
                row.put("huisnummer", adresNietBag.get("huisnummer"));
                row.put("huisletter", adresNietBag.get("huisletter"));
                row.put("toevoeging", adresNietBag.get("toevoeging"));
                row.put("postcode", adresNietBag.get("postcode"));
                row.put("plaats", adresNietBag.get("woonplaats"));
            }
        }

        JSONObject obj = rowToJson(row, true, true);
        JSONObject hoofdpand = obj.getJSONObject("hoofdpand");
        hoofdpand.remove("geom");
        JSONArray subpanden = obj.optJSONArray("subpanden");
        if(subpanden != null) {
            for(int i = 0; i < subpanden.length(); i++) {
                subpanden.getJSONObject(i).remove("geom");
            }
        }

        return obj;
    }

    public static JSONObject dbkJson(Connection c, int id) throws Exception {
        List<Map<String,Object>> rows = new QueryRunner().query(c, "select " +
                "    o.*, " +
                "    st_astext(o.geom) as geometry, " +

                "    (select row_to_json(r.*) " +
                "    from (select *, st_astext(t.geom) as geometry " +
                "         from vrh.pand t " +
                "         where t.dbk_object = o.id" +
                "         and hoofd_sub = 'Hoofdpand'" +
                "         limit 1) r " +
                "    ) as hoofdpand, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select objectid, st_astext(t.geom) as geometry " +
                "         from vrh.pand t " +
                "         where t.dbk_object = o.id" +
                "         and hoofd_sub = 'Subpand') r " +
                "    ) as subpanden, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select *, st_astext(t.geom) as geometry " +
                "         from vrh.compartimentering t " +
                "         where t.dbk_object = o.id) r " +
                "    ) as compartimentering, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select objectid, symboolcod, symboolhoe, symboolgro, omschrijvi, bijzonderh, st_astext(t.geom) as geometry " +
                "         from vrh.brandweervoorziening t " +
                "         where t.dbk_object = o.id) r " +
                "    ) as brandweervoorziening, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select *, st_astext(t.geom) as geometry " +
                "         from vrh.opstelplaats t " +
                "         where t.dbk_object = o.id) r " +
                "    ) as opstelplaats, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select objectid, symboolcod, symboolgro, omschrijvi, bijzonderh, symboolhoe, st_astext(t.geom) as geometry " +
                "         from vrh.toegang_pand t " +
                "         where t.dbk_object = o.id) r " +
                "    ) as toegang_pand, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select *, st_astext(t.geom) as geometry " +
                "         from vrh.toegang_terrein t " +
                "         where t.dbk_object = o.id) r " +
                "    ) as toegang_terrein, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select objectid, symboolcod, symboolgro, bijzonderh, soort_geva, locatie, st_astext(t.geom) as geometry " +
                "         from vrh.gevaren t " +
                "         where t.dbk_object = o.id) r " +
                "    ) as gevaren, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select *, st_astext(t.geom) as geometry " +
                "         from vrh.hellingbaan t " +
                "         where t.dbk_object = o.id) r " +
                "    ) as hellingbaan, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select *, st_astext(t.geom) as geometry " +
                "         from vrh.gevaarlijke_stoffen t " +
                "         where t.dbk_object = o.id " +
                "         order by volgnummer) r " +
                "    ) as gevaarlijke_stoffen, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select objectid, type, bijzonderh, st_astext(t.geom) as geometry " +
                "         from vrh.overige_lijnen t " +
                "         where t.dbk_object = o.id) r " +
                "    ) as overige_lijnen, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select *, st_astext(t.geom) as geometry " +
                "         from vrh.slagboom t " +
                "         where t.dbk_object = o.id) r " +
                "    ) as slagboom, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select *, st_astext(t.geom) as geometry " +
                "         from vrh.aanpijling t " +
                "         where t.dbk_object = o.id) r " +
                "    ) as aanpijling, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select objectid, tekst, symboolgro, symboolhoe, st_astext(t.geom) as geometry " +
                "         from vrh.teksten t " +
                "         where t.dbk_object = o.id) r " +
                "    ) as teksten, " +

                "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                "    from (select *, st_astext(t.geom) as geometry " +
                "         from vrh.aanrijroute t " +
                "         where t.dbk_object = o.id) r " +
                "    ) as aanrijroute " +

                "from vrh.dbk_object o " +
                "where o.id = ?", new MapListHandler(), id);

        if(rows.isEmpty()) {
            throw new IllegalArgumentException("DBK met ID " + id + " niet gevonden");
        }

        return rowToJson(rows.get(0), true, true);
    }

    public static JSONArray waterongevallenJson(Connection c, boolean newDbSchema) throws Exception {
        String sql;
        String schema = (newDbSchema ? "vrh_new" : "vrh");
        sql = "select id,locatie,adres,plaatsnaam,st_astext(selectiekader) as selectiekader, box2d(geom)::varchar as extent, st_astext(st_centroid(geom)) as geometry "
            + "from"
            + "(select id, locatie, adres, plaatsnaam, coalesce(sk.geom,wdbk.geom) as geom, sk.geom as selectiekader "
            + " from " + schema + ".wdbk_waterbereikbaarheidskaart wdbk "
            + " left join " + schema + ".waterbereikbaarheidskaart_selectiekader sk on (sk.dbk_object = wdbk.id) "
            + " where locatie is not null "
            + " order by locatie) s";
        List<Map<String,Object>> rows = new QueryRunner().query(c, sql, new MapListHandler());

        // Deduplicate based on ID
        List<Map<String,Object>> dedupRows = new ArrayList();
        Set<Long> ids = new HashSet();
        for(Map<String,Object> row: rows) {
            Object o = row.get("id");
            Long thisId = null;
            // BigDecimal for old JDBC driver              
            if(o instanceof BigDecimal) {
                thisId = ((BigDecimal)o).longValue();
            } else if(o instanceof Long) {
                thisId = (Long)o;
            }
            if(ids.contains(thisId)) {
                log.warn("Duplicate wbbk row for id " + thisId);
            } else {
                ids.add(thisId);
                dedupRows.add(row);
            }
        }

        return rowsToJSONArray(dedupRows);
    }

    public static JSONObject waterongevallenkaartJson(Connection c, Integer id, boolean newDbSchema) throws Exception {
        JSONObject result = new JSONObject();
        result.put("success", true);
        QueryRunner qr = new QueryRunner();
        String schema = (newDbSchema ? "vrh_new" : "vrh");
        List<Map<String,Object>> rows = qr.query(c, "select *, st_astext(geom) as geometry from " + schema + ".wdbk_waterbereikbaarheidskaart where id = ?", new MapListHandler(), id);
        if(rows.isEmpty()) {
            result.put("error", "WBBK met ID " + id + " niet gevonden");
        } else {
            JSONObject attributes = new JSONObject();
            result.put("attributes", attributes);

            JSONObject wbbkVlak = new JSONObject();
            wbbkVlak.put("id", "wbbk_" + id);
            wbbkVlak.put("type", "Dieptevlak tot 4 meter");

            Map<String,Object> row = rows.get(0);
            for(Map.Entry<String,Object> column: row.entrySet()) {
                if("geometry".equals(column.getKey())) {
                    wbbkVlak.put("geometry", column.getValue());
                } else if(!"geom".equals(column.getKey())) {
                    attributes.put(column.getKey(), column.getValue());
                }
            }

            String table = schema + (newDbSchema ? ".symbolen" : ".voorzieningen_water");
            rows = qr.query(c, "select id, symboolcod, symboolgro, bijzonderh, st_astext(geom) as geometry from " + table + " where dbk_object = ?", new MapListHandler(), id);
            result.put("symbolen", rowsToJSONArray(rows));

            table = schema + (newDbSchema ? ".lijnen" : ".overige_lijnen");
            rows = qr.query(c, "select id, type, bijzonderh, opmerkinge, st_astext(geom) as geometry from " + table + " where dbk_object = ?", new MapListHandler(), id);
            result.put("lijnen", rowsToJSONArray(rows));

            table = schema + (newDbSchema ? ".vlakken" : ".overige_vlakken");
            rows = qr.query(c, "select id, type, bijzonderh, st_astext(geom) as geometry from " + table + " where dbk_object = ? order by \n" +
                "   case type \n" +
                "   when 'Dieptevlak 4 tot 9 meter' then -3 \n" +
                "   when 'Dieptevlak 9 tot 15 meter' then -2\n" +
                "   when 'Dieptevlak 15 meter en dieper' then -1\n" +
                "   else id\n" +
                "   end asc", new MapListHandler(), id);

            JSONArray vlakken = new JSONArray();
            vlakken.put(wbbkVlak);
            for(Map<String,Object> r: rows) {
                vlakken.put(JSONUtils.rowToJson(r, true, true));
            }
            result.put("vlakken", vlakken);

            rows = qr.query(c, "select objectid, tekst, hoek, st_astext(geom) as geometry from " + schema + ".teksten where dbk_object = ?", new MapListHandler(), id);
            result.put("teksten", rowsToJSONArray(rows));
        }

        return result;
    }

    public static JSONArray evenementenJson(Connection c) throws Exception {
        List<Map<String,Object>> rows = new QueryRunner().query(c, "select objectid as id, evnaam, evstatus, sbegin, st_astext(st_centroid(geom)) as centroid, box2d(geom)::varchar as extent, st_astext(geom) as selectiekader from vrh.evterreinvrhobj order by evnaam", new MapListHandler());

        JSONArray objects = new JSONArray();
        for(Map<String,Object> row: rows) {
            objects.put(JSONUtils.rowToJson(row, true, true));
        }
        return objects;
    }

    public static JSONObject evenementJson(Connection c, int id) throws Exception {
        QueryRunner qr = new QueryRunner();
        JSONObject o = new JSONObject();

        JSONArray t = rowsToJSONArray(qr.query(c, "select *, st_astext(geom) as geom from vrh.evterreinvrhobj where objectid = ?", new MapListHandler(), id));
        String evnaam = t.getJSONObject(0).getString("evnaam");
        o.put("terrein", t.getJSONObject(0));
        o.put("teksten", rowsToJSONArray(qr.query(c, "select tekstreeks, teksthoek, tekstgroot, st_x(geom) as x, st_y(geom) as y from vrh.evenementen_tekst where evnaam = ?", new MapListHandler(), evnaam)));
        o.put("locatie_punt", rowsToJSONArray(qr.query(c, "select evenemento as type, ballonteks, hoek, st_x(geom) as x, st_y(geom) as y from vrh.evlocatiepuntobj where evnaam = ?", new MapListHandler(), evnaam)));
        o.put("locatie_vlak", rowsToJSONArray(qr.query(c, "select vlaksoort, omschrijvi, st_astext(geom) as geom from vrh.evlocatievlakobj where evnaam = ?", new MapListHandler(), evnaam)));
        o.put("locatie_lijn", rowsToJSONArray(qr.query(c, "select lijnsoort, lijnbeschr, st_astext(geom) as geom from vrh.evlocatielijnobj where evnaam = ?", new MapListHandler(), evnaam)));
        o.put("route_punt", rowsToJSONArray(qr.query(c, "select routepunts as soort, ballonteks, hoek, c077e6f4 as hoek2, st_x(geom) as x, st_y(geom) as y from vrh.evroutepuntobj where evnaam = ?", new MapListHandler(), evnaam)));
        o.put("route_vlak", rowsToJSONArray(qr.query(c, "select vlaksoort, vlakomschr, st_astext(geom) as geom from vrh.evroutevlakobj where evnaam = ?", new MapListHandler(), evnaam)));
        o.put("route_lijn", rowsToJSONArray(qr.query(c, "select routetype, routebesch, st_astext(geom) as geom from vrh.evroutelijnobj where evnaam = ?", new MapListHandler(), evnaam)));

        return o;
    }
}
