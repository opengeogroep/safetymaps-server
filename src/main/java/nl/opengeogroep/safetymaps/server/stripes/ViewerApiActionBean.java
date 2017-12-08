package nl.opengeogroep.safetymaps.server.stripes;

import java.io.StringReader;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.Validate;
import nl.b3p.web.stripes.ErrorMessageResolution;
import static nl.opengeogroep.safetymaps.server.db.JsonExceptionUtils.*;
import nl.opengeogroep.safetymaps.server.db.DB;
import nl.opengeogroep.safetymaps.viewer.ViewerDataExporter;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * API for online safetymaps-viewer sites.
 *
 * /api/organisation.json: general settings
 * /api/features.json: objects for creator objects, with ETag caching
 * /api/object/n.json: creator object details
 * /api/library.json: creator library
 * /api/autocomplete/search: BAG search (TODO)
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/api/{path}")
public class ViewerApiActionBean implements ActionBean {
    private static final Log log = LogFactory.getLog(ViewerApiActionBean.class);

    private static final String FEATURES = "features.json";
    private static final String ORGANISATION = "organisation.json";
    private static final String STYLES = "styles.json";
    private static final String LIBRARY = "library.json";
    private static final String OBJECT = "object/";
    private static final String AUTOCOMPLETE = "autocomplete/";

    private ActionBeanContext context;

    @Validate
    private String path;

    @Validate
    private int version = 1;

    @Validate
    private int indent = 0;

    @Validate
    private int srid = 28992;

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

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getIndent() {
        return indent;
    }

    public void setIndent(int indent) {
        this.indent = indent;
    }

    public int getSrid() {
        return srid;
    }

    public void setSrid(int srid) {
        this.srid = srid;
    }

    public Resolution api() {

        try(Connection c = DB.getConnection()) {
            if(path != null) {
                if(FEATURES.equals(path)) {
                    return features(c);
                }
                if(ORGANISATION.equals(path)) {
                    return organisation(c);
                }
                if(STYLES.equals(path)) {
                    return styles(c);
                }
                if(LIBRARY.equals(path)) {
                    return library(c);
                }
                if(path.indexOf(OBJECT) == 0) {
                    return object(c);
                }
                if(path.indexOf(AUTOCOMPLETE) == 0) {
                    return autocomplete(c);
                }
            }

            return new ErrorResolution(HttpServletResponse.SC_NOT_FOUND, "Not found: /api/" + path);
        } catch(Exception e) {
            return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Error on /api/" + path, e).toString(indent));
        }
    }

    private Resolution features(Connection c) {

        ViewerDataExporter vde = new ViewerDataExporter(c);
        try {
            final String etag = '"' + vde.getObjectsETag() + '"';

            String ifNoneMatch = getContext().getRequest().getHeader("If-None-Match");
            if(ifNoneMatch != null && ifNoneMatch.contains(etag)) {
                return new ErrorResolution(HttpServletResponse.SC_NOT_MODIFIED);
            }

            final JSONObject o = version < 3 ? getFeaturesLegacy(c) : getFeaturesJson(vde);

            return new Resolution() {
                @Override
                public void execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
                    String encoding = "UTF-8";
                    response.setCharacterEncoding(encoding);
                    response.setContentType("application/json");
                    response.addHeader("ETag", etag);

                    // Compression to be done at webserver level

                    IOUtils.copy(new StringReader(o.toString(indent)), response.getOutputStream(), encoding);
                }
            };

        } catch(Exception e) {
            return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Error getting viewer objects", e).toString(indent));
        }
    }

    private JSONObject getFeaturesLegacy(Connection c) throws Exception {
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

    private JSONObject getFeaturesJson(ViewerDataExporter vde) throws Exception {
        JSONObject o = new JSONObject("{success:true}");
        o.put("results", vde.getViewerObjectMapOverview());
        return o;
    }

    private Resolution organisation(Connection c) throws Exception {
        Object org = new QueryRunner().query(c, "select \"organisation\" from organisation.organisation_nieuw_json(" + srid + ")", new ScalarHandler<>());
        JSONObject j = new JSONObject();
        j.put("organisation", new JSONObject(org.toString()));
        return new StreamingResolution("application/json", j.toString(indent));
    }

    private Resolution library(Connection c) throws Exception {
        JSONArray a = new JSONArray();

        List<Map<String,Object>> rows = new QueryRunner().query(c, "select \"ID\",\"Omschrijving\",\"Documentnaam\" from wfs.\"Bibliotheek\" order by \"Omschrijving\"", new MapListHandler());
        for(Map<String,Object> r: rows) {
            JSONObject j = new JSONObject();
            for(Map.Entry<String,Object> entry: r.entrySet()) {
                j.put(entry.getKey(), entry.getValue());
            }
            a.put(j);
        }
        JSONObject library = new JSONObject();
        library.put("success", true);
        library.put("items", a);
        return new StreamingResolution("application/json", library.toString(indent));
    }

    private Resolution styles(Connection c) throws Exception {
        JSONObject o = new ViewerDataExporter(c).getStyles();
        return new StreamingResolution("application/json", o.toString(indent));
    }

    private Resolution object(Connection c) throws Exception {
        Pattern p = Pattern.compile("object\\/([0-9]+)\\.json");
        Matcher m = p.matcher(path);

        if(!m.find()) {
            return new ErrorResolution(HttpServletResponse.SC_NOT_FOUND, "No object id found: /api/" + path);
        }

        int id = Integer.parseInt(m.group(1));

        JSONObject o = null;
        if(version == 3) {
            o = new ViewerDataExporter(c).getViewerObjectDetails(id);
        } else {
            Object json = new QueryRunner().query(c, "select \"DBKObject\" from " + (version == 2 ? "dbk2" : "dbk") + ".dbkobject_json(?)", new ScalarHandler(), id);
            if(json != null) {
                o = new JSONObject();
                o.put("DBKObject", new JSONObject(json.toString()));
            }
        }

        if(o == null) {
            return new ErrorResolution(HttpServletResponse.SC_NOT_FOUND, "Object id not found: " + id);
        } else {
            return new StreamingResolution("application/json", o.toString(indent));
        }
    }

    private Resolution autocomplete(Connection c) {
        // TODO old controllers/bag.js

        return new ErrorMessageResolution(500, "Not implemented");
    }
}
