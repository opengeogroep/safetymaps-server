package nl.opengeogroep.safetymaps.server.stripes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.*;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.DB;
import static nl.opengeogroep.safetymaps.server.db.GeoJSONUtils.*;
import nl.opengeogroep.safetymaps.server.db.JSONUtils;
import static nl.opengeogroep.safetymaps.server.db.JSONUtils.rowToJson;
import static nl.opengeogroep.safetymaps.server.db.JsonExceptionUtils.logExceptionAndReturnJSONObject;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.IOUtils;
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

    @Validate(required = true, on = {"wbbk", "dbk", "evenement"})
    private String id;

    @Validate
    private int indent = 0;

    @Validate
    private boolean wkt = false;

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

    public boolean isWkt() {
        return wkt;
    }

    public void setWkt(boolean wkt) {
        this.wkt = wkt;
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
        JSONObject wbbks = wbbksJson(false);
        out.write(wbbks.toString(4).getBytes("UTF-8"));

        if(wbbks.has("wbbk")) {
            JSONArray features = wbbks.getJSONObject("wbbk").getJSONArray("features");
            for(int i = 0; i < features.length(); i++) {
                int id = features.getJSONObject(i).getJSONObject("properties").getInt("id");
                e = new ZipEntry("api/wbbk/" + id + ".json");
                out.putNextEntry(e);
                JSONObject wbbk = wbbkJson(id, false);
                out.write(wbbk.toString(4).getBytes("UTF-8"));
            }
        } // else has "error"

        // TODO dbk features

        // TODO evenement features

        out.flush();
        out.close();

        return new StreamingResolution("application/zip", new ByteArrayInputStream(b.toByteArray())).setFilename("wbbk-api.zip");
    }

    private static JSONObject wbbksJson(boolean wkt) {
        JSONObject result = new JSONObject();
        result.put("success", false);
        try {
            String sql;
            if(wkt) {
                sql = "select id,locatie,adres,plaatsnaam,st_astext(selectiekader) as selectiekader, box2d(geom)::varchar as extent, st_astext(st_centroid(geom)) as geometry "
                    + "from"
                    + "(select id, locatie, adres, plaatsnaam, coalesce(sk.geom,wdbk.geom) as geom, sk.geom as selectiekader "
                    + " from vrh.wdbk_waterbereikbaarheidskaart wdbk "
                    + " left join vrh.waterbereikbaarheidskaart_selectiekader sk on (sk.dbk_object = wdbk.id) "
                    + " where locatie is not null "
                    + " order by locatie) s";
            } else {
                sql = "select id,locatie,adres,plaatsnaam,st_asgeojson(selectiekader) as selectiekader, st_xmin(geom)||','||st_ymin(geom)||','||st_xmax(geom)||','||st_ymax(geom) as bounds, st_asgeojson(st_centroid(geom)) as geometry "
                    + "from"
                    + "(select id, locatie, adres, plaatsnaam, coalesce(sk.geom,wdbk.geom) as geom, sk.geom as selectiekader "
                    + " from vrh.wdbk_waterbereikbaarheidskaart wdbk "
                    + " left join vrh.waterbereikbaarheidskaart_selectiekader sk on (sk.dbk_object = wdbk.id) "
                    + " where locatie is not null "
                    + " order by locatie) s";
            }
            List<Map<String,Object>> rows = DB.qr().query(sql, new MapListHandler());

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

            if(wkt) {
                result.put("results", rowsToJSONArray(dedupRows));
            } else {
                result.put("wbbk", rowsToGeoJSONFeatureCollection(dedupRows));
            }
            result.put("success", true);
        } catch(Exception e) {
            log.error("Error getting VRH wbbk data", e);
            result.put("error", "Fout ophalen WBBK data: " + e.getClass() + ": " + e.getMessage());

        }
        return result;
    }

    @DefaultHandler
    public Resolution def() {
        JSONObject result = new JSONObject();
        result.put("error", "No handler specified");
        result.put("success", false);
        return new StreamingResolution("application/json", result.toString(indent));
    }

    public Resolution wbbks() {
        JSONObject result = wbbksJson(wkt);
        context.getResponse().addHeader("Access-Control-Allow-Origin", "*");
        return new StreamingResolution("application/json", result.toString(indent));
    }

    private static JSONObject wbbkJson(Integer id, boolean wkt) {

        JSONObject result = new JSONObject();
        result.put("success", false);
        QueryRunner qr = new QueryRunner();
        try(Connection c = DB.getConnection()) {
            String geomFunction = (wkt ? "st_astext" : "st_asgeojson");
            List<Map<String,Object>> rows = qr.query(c, "select *, " + geomFunction + "(geom) as geometry from vrh.wdbk_waterbereikbaarheidskaart where id = ?", new MapListHandler(), id);
            if(rows.isEmpty()) {
                result.put("error", "WBBK met ID " + id + " niet gevonden");
            } else {
                JSONObject attributes = new JSONObject();
                result.put("attributes", attributes);

                JSONObject wbbkVlak = new JSONObject();
                if(wkt) {
                    wbbkVlak.put("id", "wbbk_" + id);
                    wbbkVlak.put("type", "Dieptevlak tot 4 meter");
                } else {
                    wbbkVlak.put("type", "Feature");
                    JSONObject props = new JSONObject();
                    wbbkVlak.put("properties", props);
                    props.put("id", "wbbk_" + id);
                    props.put("type", "Dieptevlak tot 4 meter");
                }

                Map<String,Object> row = rows.get(0);
                for(Map.Entry<String,Object> column: row.entrySet()) {
                    if("geometry".equals(column.getKey())) {
                        if(wkt) {
                            wbbkVlak.put("geometry", column.getValue());
                        } else {
                            wbbkVlak.put("geometry", new JSONObject((String)column.getValue()));
                        }
                    } else if(!"geom".equals(column.getKey())) {
                        attributes.put(column.getKey(), column.getValue());
                    }
                }

                rows = qr.query(c, "select id, symboolcod, symboolgro, bijzonderh, " + geomFunction + "(geom) as geometry from vrh.voorzieningen_water where dbk_object = ?", new MapListHandler(), id);
                result.put("symbolen", wkt ? rowsToJSONArray(rows) : rowsToGeoJSONFeatureCollection(rows));

                rows = qr.query(c, "select id, type, bijzonderh, opmerkinge, " + geomFunction + "(geom) as geometry from vrh.overige_lijnen where dbk_object = ?", new MapListHandler(), id);
                result.put("lijnen", wkt ? rowsToJSONArray(rows) : rowsToGeoJSONFeatureCollection(rows));

                rows = qr.query(c, "select id, type, bijzonderh, " + geomFunction + "(geom) as geometry from vrh.overige_vlakken where dbk_object = ? order by \n" +
                    "   case type \n" +
                    "   when 'Dieptevlak 4 tot 9 meter' then -3 \n" +
                    "   when 'Dieptevlak 9 tot 15 meter' then -2\n" +
                    "   when 'Dieptevlak 15 meter en dieper' then -1\n" +
                    "   else id\n" +
                    "   end asc", new MapListHandler(), id);

                if(wkt) {
                    JSONArray vlakken = new JSONArray();
                    vlakken.put(wbbkVlak);
                    for(Map<String,Object> r: rows) {
                        vlakken.put(JSONUtils.rowToJson(r, true, true));
                    }
                    result.put("vlakken", vlakken);
                } else {
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
                }

                rows = qr.query(c, "select objectid, tekst, hoek, " + geomFunction + "(geom) as geometry from vrh.teksten where dbk_object = ?", new MapListHandler(), id);
                result.put("teksten", wkt ? rowsToJSONArray(rows) : rowsToGeoJSONFeatureCollection(rows));

                result.put("success", true);
            }
        } catch(Exception e) {
            log.error("Error getting VRH wbbk data", e);
            result.put("error", "Fout ophalen WBBK data: " + e.getClass() + ": " + e.getMessage());
        }
        return result;
    }

    public Resolution wbbk() {
        if(wkt) {
            try {
                JSONObject r = new JSONObject();
                r.put("success", true);
                r.put("results",  wbbkJson(Integer.parseInt(id), wkt));
                return new StreamingResolution("application/json", r.toString(indent));
            } catch(Exception e) {
                return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Error on " + getContext().getRequest().getRequestURI(), e).toString(indent));
            }
        } else {
            JSONObject result = wbbkJson(Integer.parseInt(id), wkt);
            return new StreamingResolution("application/json", result.toString(indent));
        }
    }

    private static JSONArray dbksJson(Connection c) throws Exception {
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

    public Resolution dbks() {

        try(Connection c = DB.getConnection()) {
            final long lastModified = new QueryRunner().query(c, "select time from vrh.import_metadata limit 1", new ScalarHandler<Timestamp>()).getTime() / 1000;
            long ifModifiedSince = getContext().getRequest().getDateHeader("If-Modified-Since") / 1000;

            if(ifModifiedSince >= lastModified) {
                return new ErrorResolution(HttpServletResponse.SC_NOT_MODIFIED);
            }

            final JSONObject r = new JSONObject();
            r.put("success", true);
            r.put("results", dbksJson(c));

            return new Resolution() {
                @Override
                public void execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
                    String encoding = "UTF-8";
                    response.setCharacterEncoding(encoding);
                    response.setContentType("application/json");
                    response.addDateHeader("Last-Modified", lastModified * 1000);

                    OutputStream out;
                    String acceptEncoding = request.getHeader("Accept-Encoding");
                    if(acceptEncoding != null && acceptEncoding.contains("gzip")) {
                        response.setHeader("Content-Encoding", "gzip");
                        out = new GZIPOutputStream(response.getOutputStream(), true);
                    } else {
                        out = response.getOutputStream();
                    }
                    IOUtils.copy(new StringReader(r.toString(indent)), out, encoding);
                    out.flush();
                    out.close();
                }
            };
        } catch(Exception e) {
            return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Error on " + getContext().getRequest().getRequestURI(), e).toString(indent));
        }
    }

    private static JSONObject dbkJson(int id) throws Exception {
        try(Connection c = DB.getConnection()) {
            List<Map<String,Object>> rows = new QueryRunner().query(c, "select " +
                    "    o.*, " +
                    "    st_astext(o.geom) as geometry, " +

                    "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                    "    from (select *, st_astext(t.geom) as geometry " +
                    "         from vrh.pand t " +
                    "         where t.dbk_object = o.id) r " +
                    "    ) as pand, " +

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
                    "    from (select *, st_astext(t.geom) as geometry " +
                    "         from vrh.toegang_pand t " +
                    "         where t.dbk_object = o.id) r " +
                    "    ) as toegang_pand, " +

                    "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                    "    from (select *, st_astext(t.geom) as geometry " +
                    "         from vrh.toegang_terrein t " +
                    "         where t.dbk_object = o.id) r " +
                    "    ) as toegang_terrein, " +

                    "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                    "    from (select *, st_astext(t.geom) as geometry " +
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
                    "    from (select *, st_astext(t.geom) as geometry " +
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
                    "    from (select *, st_astext(t.geom) as geometry " +
                    "         from vrh.teksten t " +
                    "         where t.dbk_object = o.id) r " +
                    "    ) as teksten, " +

                    "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                    "    from (select *, st_astext(t.geom) as geometry " +
                    "         from vrh.aanrijroute t " +
                    "         where t.dbk_object = o.id) r " +
                    "    ) as aanrijroute, " +

                    "    (select array_to_json(array_agg(row_to_json(r.*))) " +
                    "    from (select *, st_astext(t.geom) as geometry " +
                    "         from vrh.nevendbkadres t " +
                    "         where t.dbk_object = o.id) r " +
                    "    ) as nevendbkadres " +

                    "from vrh.dbk_object o " +
                    "where o.id = ?", new MapListHandler(), id);

            if(rows.isEmpty()) {
                throw new IllegalArgumentException("DBK met ID " + id + " niet gevonden");
            }

            return rowToJson(rows.get(0), true, true);
        }
    }

    public Resolution dbk() {
        JSONObject result = new JSONObject();
        result.put("success", false);
        try {
            result.put("results", dbkJson(Integer.parseInt(id)));
            result.put("success", true);
            return new StreamingResolution("application/json", result.toString(indent));
        } catch(Exception e) {
            return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Fout ophalen DBK data voor ID  " + id, e).toString(indent));
        }
    }

    private JSONArray evenementenJson() throws Exception {
        QueryRunner qr = new QueryRunner();
        try(Connection c = DB.getConnection()) {
            JSONArray objects = new JSONArray();

            List<Map<String,Object>> rows = qr.query(c, "select objectid as id, evnaam, evstatus, sbegin, st_astext(st_centroid(geom)) as centroid, box2d(geom)::varchar as extent, st_astext(geom) as selectiekader from vrh.evterreinvrhobj order by evnaam", new MapListHandler());

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

            JSONArray t = rowsToJSONArray(qr.query(c, "select *, st_astext(geom) as geom from vrh.evterreinvrhobj where evnaam = ?", new MapListHandler(), name));
            o.put("terrein", t.getJSONObject(0));
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
