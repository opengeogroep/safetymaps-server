package nl.opengeogroep.safetymaps.server.stripes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.*;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.DB;
import static nl.opengeogroep.safetymaps.server.db.GeoJSONUtils.*;
import nl.opengeogroep.safetymaps.server.db.JSONUtils;
import static nl.opengeogroep.safetymaps.server.db.JsonExceptionUtils.logExceptionAndReturnJSONObject;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/api/vrh")
public class VrhActionBean implements ActionBean {
    private ActionBeanContext context;

    private static final Log log = LogFactory.getLog(VrhActionBean.class);

    @Validate
    private String id;

    @Validate
    private int indent = 0;

    // <editor-fold defaultstate="collapsed" desc="getters and setters">
    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getIndent() {
        return indent;
    }

    public void setIndent(int indent) {
        this.indent = indent;
    }
    // </editor-fold>

    /**
     * Create all data for static viewer
     */
    public Resolution staticData() throws IOException {
        if(!"127.0.0.1".equals(getContext().getRequest().getRemoteAddr())) {
            return new ErrorMessageResolution(HttpServletResponse.SC_FORBIDDEN, "Access denied!");
        }

        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ZipOutputStream out = new ZipOutputStream(b);
        ZipEntry e = new ZipEntry("api/wbbks.json");
        out.putNextEntry(e);
        JSONObject wbbks = wbbksJson();
        out.write(wbbks.toString(4).getBytes("UTF-8"));

        if(wbbks.has("wbbk")) {
            JSONArray features = wbbks.getJSONObject("wbbk").getJSONArray("features");
            for(int i = 0; i < features.length(); i++) {
                int id = features.getJSONObject(i).getJSONObject("properties").getInt("id");
                e = new ZipEntry("api/wbbk/" + id + ".json");
                out.putNextEntry(e);
                JSONObject wbbk = wbbkJson(id);
                out.write(wbbk.toString(4).getBytes("UTF-8"));
            }
        } // else has "error"

        // TODO dbk features

        // TODO evenement features

        out.flush();
        out.close();

        return new StreamingResolution("application/zip", new ByteArrayInputStream(b.toByteArray())).setFilename("wbbk-api.zip");
    }

    private static JSONObject wbbksJson() {
        JSONObject result = new JSONObject();
        result.put("success", false);
        try {
            List<Map<String,Object>> rows = DB.qr().query("select id,locatie,adres,plaatsnaam,st_asgeojson(selectiekader) as selectiekader, st_xmin(geom)||','||st_ymin(geom)||','||st_xmax(geom)||','||st_ymax(geom) as bounds, st_asgeojson(st_centroid(geom)) as geometry "
                    + "from"
                    + "(select id, locatie, adres, plaatsnaam, coalesce(sk.geom,wdbk.geom) as geom, sk.geom as selectiekader "
                    + " from vrh.wdbk_waterbereikbaarheidskaart wdbk "
                    + " left join vrh.waterbereikbaarheidskaart_selectiekader sk on (sk.dbk_object = wdbk.id) "
                    + " where locatie is not null "
                    + " order by locatie) s", new MapListHandler());

            // Deduplicate based on ID
            List<Map<String,Object>> dedupRows = new ArrayList();
            Set<BigDecimal> ids = new HashSet();
            for(Map<String,Object> row: rows) {
                BigDecimal thisId = (BigDecimal)row.get("id");
                if(ids.contains(thisId)) {
                    log.warn("Duplicate wbbk row for id " + thisId);
                } else {
                    ids.add(thisId);
                    dedupRows.add(row);
                }
            }

            result.put("wbbk", rowsToGeoJSONFeatureCollection(dedupRows));
            result.put("success", true);
        } catch(Exception e) {
            log.error("Error getting VRH wbbk data", e);
            result.put("error", "Fout ophalen WBBK data: " + e.getClass() + ": " + e.getMessage());

        }
        return result;
    }

    @DefaultHandler
    public Resolution wbbks() {
        JSONObject result = wbbksJson();
        context.getResponse().addHeader("Access-Control-Allow-Origin", "*");
        return new StreamingResolution("application/json", result.toString(indent));
    }

    private static JSONObject wbbkJson(Integer id) {

        JSONObject result = new JSONObject();
        result.put("success", false);
        QueryRunner qr = new QueryRunner();
        try(Connection c = DB.getConnection()) {
            List<Map<String,Object>> rows = qr.query(c, "select *, st_asgeojson(geom) as geometry from vrh.wdbk_waterbereikbaarheidskaart where id = ?", new MapListHandler(), id);
            if(rows.isEmpty()) {
                result.put("error", "WBBK met ID " + id + " niet gevonden");
            } else {
                JSONObject attributes = new JSONObject();
                result.put("attributes", attributes);

                JSONObject wbbkVlak = new JSONObject();
                wbbkVlak.put("type", "Feature");
                JSONObject props = new JSONObject();
                wbbkVlak.put("properties", props);
                props.put("id", "wbbk_" + id);
                props.put("type", "Dieptevlak tot 4 meter");

                Map<String,Object> row = rows.get(0);
                for(Map.Entry<String,Object> column: row.entrySet()) {
                    if("geometry".equals(column.getKey())) {
                        wbbkVlak.put("geometry", new JSONObject((String)column.getValue()));
                    } else if(!"geom".equals(column.getKey())) {
                        attributes.put(column.getKey(), column.getValue());
                    }
                }

                rows = qr.query(c, "select id, symboolcod, symboolgro, bijzonderh, st_asgeojson(geom) as geometry from vrh.voorzieningen_water where dbk_object = ?", new MapListHandler(), id);
                result.put("symbolen", rowsToGeoJSONFeatureCollection(rows));

                rows = qr.query(c, "select id, type, bijzonderh, opmerkinge, st_asgeojson(geom) as geometry from vrh.overige_lijnen where dbk_object = ?", new MapListHandler(), id);
                result.put("lijnen", rowsToGeoJSONFeatureCollection(rows));

                rows = qr.query(c, "select id, type, bijzonderh, st_asgeojson(geom) as geometry from vrh.overige_vlakken where dbk_object = ? order by \n" +
                    "   case type \n" +
                    "   when 'Dieptevlak 4 tot 9 meter' then -3 \n" +
                    "   when 'Dieptevlak 9 tot 15 meter' then -2\n" +
                    "   when 'Dieptevlak 15 meter en dieper' then -1\n" +
                    "   else id\n" +
                    "   end asc", new MapListHandler(), id);

                JSONObject vlakken = new JSONObject();
                vlakken.put("type", "FeatureCollection");
                JSONArray vlakkenFeatures = new JSONArray();
                vlakken.put("features", vlakkenFeatures);
                vlakkenFeatures.put(wbbkVlak);
                JSONObject vlakkenFC = rowsToGeoJSONFeatureCollection(rows);
                JSONArray features = vlakkenFC.getJSONArray("features");
                for(int i = 0; i < features.length(); i++) {
                    vlakkenFeatures.put(features.get(i));
                }
                vlakkenFC.put("features", vlakken);
                result.put("vlakken", vlakken);

                rows = qr.query(c, "select objectid, tekst, hoek, st_asgeojson(geom) as geometry from vrh.teksten where dbk_object = ?", new MapListHandler(), id);
                result.put("teksten", rowsToGeoJSONFeatureCollection(rows));

                result.put("success", true);
            }
        } catch(Exception e) {
            log.error("Error getting VRH wbbk data", e);
            result.put("error", "Fout ophalen WBBK data: " + e.getClass() + ": " + e.getMessage());
        }
        return result;
    }

    public Resolution wbbk() {
        JSONObject result = wbbkJson(Integer.parseInt(id));
        context.getResponse().addHeader("Access-Control-Allow-Origin", "*");
        return new StreamingResolution("application/json", result.toString(4));
    }

    private static JSONArray dbksJson() throws Exception {
        QueryRunner qr = new QueryRunner();
        try(Connection c = DB.getConnection()) {
            JSONArray objects = new JSONArray();

            List<Map<String,Object>> rows = qr.query(c, "select o.id, naam, oms_nummer, straatnaam, huisnummer, huisletter, toevoeging, postcode, plaats, " +
                    "st_astext(st_centroid(o.geom)) as pand_centroid, box2d(o.geom)::varchar as extent " +
                    "from vrh.dbk_object o " +
                    "left join vrh.pand p on (p.dbk_object = o.id) " +
                    "where p.hoofd_sub='Hoofdpand'", new MapListHandler());

            List<Map<String,Object>> adressen = qr.query(c, "select dbk_object as id, " +
                    "        (select array_to_json(array_agg(row_to_json(r.*))) " +
                    "         from (select na.postcode as pc, na.woonplaats as pl, na.straatnaam as sn,  " +
                    "                array_to_json(array_agg(distinct na.huisnummer || case when na.huisletter <> '' or na.toevoeging <> '' then '|' || coalesce(na.huisletter,'') || '|' || coalesce(na.toevoeging,'') else '' end)) as nrs " +
                    "                from vrh.nevendbkadres na " +
                    "                where na.dbk_object = t.dbk_object " +
                    "                group by na.postcode, na.woonplaats, na.straatnaam) r " +
                    "        ) as selectieadressen " +
                    "    from (select distinct dbk_object from vrh.nevendbkadres where huisletter is not null) t", new MapListHandler());

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
    }

    public Resolution dbks() {

        // TODO caching, controlled by shapefile import script executing query

        try {
            JSONObject r = new JSONObject();
            r.put("success", true);
            r.put("results", dbksJson());
            return new StreamingResolution("application/json", r.toString(indent));
        } catch(Exception e) {
            return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Error on " + getContext().getRequest().getRequestURI(), e).toString(indent));
        }
    }

    private JSONArray evenementenJson() throws Exception {
        QueryRunner qr = new QueryRunner();
        try(Connection c = DB.getConnection()) {
            JSONArray objects = new JSONArray();

            List<Map<String,Object>> rows = qr.query(c, "select objectid as id, evnaam, evstatus, sbegin, titel, st_astext(st_centroid(geom)) as centroid, box2d(geom)::varchar as extent, st_astext(geom) as selectiekader from vrh.evenementen_index", new MapListHandler());

            for(Map<String,Object> row: rows) {
                objects.put(JSONUtils.rowToJson(row, true, true));
            }
            return objects;
        }
    }

    public Resolution evenementen() {
        try {
            JSONObject r = new JSONObject();
            r.put("success", true);
            r.put("results", evenementenJson());
            return new StreamingResolution("application/json", r.toString(indent));
        } catch(Exception e) {
            return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Error on " + getContext().getRequest().getRequestURI(), e).toString(indent));
        }
    }

    private JSONObject evenementJson(String name) throws Exception {
        QueryRunner qr = new QueryRunner();
        try(Connection c = DB.getConnection()) {
            JSONObject o = new JSONObject();

            o.put("teksten", rowsToJSONArray(qr.query(c, "select tekstreeks, teksthoek, tekstgroot, st_x(geom) as x, st_y(geom) as y from vrh.evenementen_tekst where evnaam = ?", new MapListHandler(), name)));
            o.put("locatie_punt", rowsToJSONArray(qr.query(c, "select evenemento as type, ballonteks, hoek, st_x(geom) as x, st_y(geom) as y from vrh.evlocatiepuntobj where evnaam = ?", new MapListHandler(), name)));
            o.put("locatie_vlak", rowsToJSONArray(qr.query(c, "select vlaksoort, omschrijvi, st_astext(geom) as geom from vrh.evlocatievlakobj where evnaam = ?", new MapListHandler(), name)));
            o.put("locatie_lijn", rowsToJSONArray(qr.query(c, "select lijnsoort, lijnbeschr, st_astext(geom) as geom from vrh.evlocatielijnobj where evnaam = ?", new MapListHandler(), name)));
            o.put("route_punt", rowsToJSONArray(qr.query(c, "select routepunts as soort, ballonteks, hoek, c077e6f4 as hoek2, st_x(geom) as x, st_y(geom) as y from vrh.evroutepuntobj where evnaam = ?", new MapListHandler(), name)));
            o.put("route_vlak", rowsToJSONArray(qr.query(c, "select vlaksoort, vlakomschr, st_astext(geom) as geom from vrh.evroutevlakobj where evnaam = ?", new MapListHandler(), name)));
            o.put("route_lijn", rowsToJSONArray(qr.query(c, "select routetype, routebesch, st_astext(geom) as geom from vrh.evroutelijnobj where evnaam = ?", new MapListHandler(), name)));

            return o;
        }
    }

    private static JSONArray rowsToJSONArray(List<Map<String,Object>> rows) throws Exception {
        JSONArray a = new JSONArray();
        for(Map<String,Object> row: rows) {
            a.put(JSONUtils.rowToJson(row, true, true));
        }
        return a;
    }

    public Resolution evenement() {
        try {
            JSONObject r = new JSONObject();
            r.put("success", true);
            r.put("results", evenementJson(id));
            return new StreamingResolution("application/json", r.toString(indent));
        } catch(Exception e) {
            return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Error on " + getContext().getRequest().getRequestURI(), e).toString(indent));
        }
    }

}
