package nl.opengeogroep.safetymaps.server.stripes;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.Validate;
import nl.opengeogroep.safetymaps.server.db.DB;
import static nl.opengeogroep.safetymaps.server.db.GeoJSONUtils.*;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;

/**
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/viewer/api/vrln/brandkranen.json")
public class VrlnActionBean implements ActionBean {
    private ActionBeanContext context;

    private static final Log log = LogFactory.getLog(VrhActionBean.class);

    @Validate
    private String bounds;

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public String getBounds() {
        return bounds;
    }

    public void setBounds(String bounds) {
        this.bounds = bounds;
    }

    public static JSONObject getData(Connection c, String bounds) throws Exception {
        JSONObject result = new JSONObject();
        result.put("success", true);
        String boundsSql = "";
        Object[] args = new Object[] {};
        if(bounds != null) {
            boundsSql = " where geom && st_setsrid(st_geomfromtext(?),28992)";
            args = new Object[] { bounds };
        }
        List<Map<String,Object>> rows = new QueryRunner().query(c, "select gid, nummer, soort, capaciteit, streng_id, st_asgeojson(geom) as geometry,straatnaam,postcode, huisnummer, plaatsnaam from vrln.brandkranen_wml" + boundsSql, new MapListHandler(), args);
        result.put("brandkranen_wml", rowsToGeoJSONFeatureCollection(rows));
        return result;
    }

    @DefaultHandler
    public Resolution data() {
        JSONObject o;
        try(Connection c = DB.getConnection()) {
            o = getData(c, bounds);
        } catch(Exception e) {
            log.error("Error getting VRLN brandkranen data", e);
            o = new JSONObject();
            o.put("success", false);
            o.put("error", "Fout ophalen VRLN brandkranen data: " + e.getClass() + ": " + e.getMessage());
        }

        context.getResponse().addHeader("Access-Control-Allow-Origin", "*");
        return new StreamingResolution("application/json", o.toString());
    }
}
