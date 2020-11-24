package nl.opengeogroep;

import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.io.StringReader;
import java.sql.Connection;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class SafetymapsApi {

    public static String test() {
        return "hello world";
    }


    public static Resolution features(Connection c, ActionBeanContext context, int version, final int indent, int srid) {

        ViewerDataExporter vde = new ViewerDataExporter(c);
        try {
            final String etag = '"' + vde.getObjectsETag() + '"';

            String ifNoneMatch = context.getRequest().getHeader("If-None-Match");
            if(ifNoneMatch != null && ifNoneMatch.contains(etag)) {
                // return new ErrorResolution(HttpServletResponse.SC_NOT_MODIFIED);
                return null;
            }

            final JSONObject o = version < 3 ? getFeaturesLegacy(c, version, srid) : getFeaturesJson(vde);

            return new Resolution() {
                @Override
                public void execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
                    String encoding = "UTF-8";
                    response.setCharacterEncoding(encoding);
                    response.setContentType("application/json");
                    response.addHeader("ETag", etag);

                    OutputStream out;
                    String acceptEncoding = request.getHeader("Accept-Encoding");
                    if(acceptEncoding != null && acceptEncoding.contains("gzip")) {
                        response.setHeader("Content-Encoding", "gzip");
                        out = new GZIPOutputStream(response.getOutputStream(), true);
                    } else {
                        out = response.getOutputStream();
                    }
                    IOUtils.copy(new StringReader(o.toString(indent)), out, encoding);
                    out.flush();
                    out.close();
                }
            };

        } catch(Exception e) {
            return new StreamingResolution("application/json", "error");
        }
    }

    private static JSONObject getFeaturesJson(ViewerDataExporter vde) throws Exception {
        JSONObject o = new JSONObject("{success:true}");
        o.put("results", vde.getViewerObjectMapOverview());
        return o;
    }

    private static JSONObject getFeaturesLegacy(Connection c, int version, int srid) throws Exception {
        JSONObject o = new JSONObject();
        o.put("type", "FeatureCollection");
        JSONArray ja = new JSONArray();
        o.put("features", ja);
        boolean version2 = version == 2;
        String from = version2 ? "dbk2.dbkfeatures_json" : " dbk.dbkfeatures_adres_json";
        List rows = (List)new QueryRunner().query(c, "select \"feature\" from " + from + "(" + srid + ")", new ColumnListHandler());
        for (Object row: rows) {
            JSONObject d = new JSONObject(row.toString());
            JSONObject j = new JSONObject();
            ja.put(j);
            j.put("type", "Feature");
            j.put("id", "DBKFeature.gid--" + d.get("gid"));
            j.put("geometry", d.get("geometry"));
            JSONObject properties = new JSONObject();
            j.put("properties", properties);
            for(Object key: d.keySet()) {
                if(!"geometry".equals(key)) {
                    properties.put((String)key, d.get((String)key));
                }
            }
        }
        return o;
    }
}
