package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
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
@UrlBinding("/action/vrh")
public class VrhActionBean implements ActionBean {
    private ActionBeanContext context;

    private static final Log log = LogFactory.getLog(VrhActionBean.class);

    @Validate
    private Integer id;

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }


    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Create all data for static voertuigviewer
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
        out.flush();
        out.close();

        return new StreamingResolution("application/zip", new ByteArrayInputStream(b.toByteArray())).setFilename("wbbk-api.zip");
    }

    private static JSONObject wbbksJson() {
        JSONObject result = new JSONObject();
        result.put("success", false);
        try {
            List<Map<String,Object>> rows = DB.qr().query("select id, locatie, adres, plaatsnaam, st_xmin(geom)||','||st_ymin(geom)||','||st_xmax(geom)||','||st_ymax(geom) as bounds, st_asgeojson(st_centroid(geom)) as geometry from vrh.wdbk_waterbereikbaarheidskaart where locatie is not null order by locatie", new MapListHandler());

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
        return new StreamingResolution("application/json", result.toString(4));
    }

    private static JSONObject wbbkJson(Integer id) {

        JSONObject result = new JSONObject();
        result.put("success", false);
        try {
            List<Map<String,Object>> rows = DB.qr().query("select *, st_asgeojson(geom) as geometry from vrh.wdbk_waterbereikbaarheidskaart where id = ?", new MapListHandler(), id);
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

                rows = DB.qr().query("select id, symboolcod, symboolgro, bijzonderh, st_asgeojson(geom) as geometry from vrh.voorzieningen_water where dbk_object = ?", new MapListHandler(), id);
                result.put("symbolen", rowsToGeoJSONFeatureCollection(rows));

                rows = DB.qr().query("select id, type, bijzonderh, opmerkinge, st_asgeojson(geom) as geometry from vrh.overige_lijnen where dbk_object = ?", new MapListHandler(), id);
                result.put("lijnen", rowsToGeoJSONFeatureCollection(rows));

                rows = DB.qr().query("select id, type, bijzonderh, st_asgeojson(geom) as geometry from vrh.overige_vlakken where dbk_object = ? order by \n" +
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

                rows = DB.qr().query("select objectid, tekst, hoek, st_asgeojson(geom) as geometry from vrh.teksten where dbk_object = ?", new MapListHandler(), id);
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
        JSONObject result = wbbkJson(id);
        context.getResponse().addHeader("Access-Control-Allow-Origin", "*");
        return new StreamingResolution("application/json", result.toString(4));
    }
}
