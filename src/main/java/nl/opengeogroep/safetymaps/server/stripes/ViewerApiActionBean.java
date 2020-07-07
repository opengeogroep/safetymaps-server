package nl.opengeogroep.safetymaps.server.stripes;

import java.io.OutputStream;
import java.io.StringReader;
import java.sql.Connection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.mock.MockHttpServletRequest;
import net.sourceforge.stripes.validation.Validate;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import static nl.opengeogroep.safetymaps.server.db.JsonExceptionUtils.*;
import nl.opengeogroep.safetymaps.server.db.DB;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_ADMIN;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_DRAWING_EDITOR;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_EIGEN_VOERTUIGNUMMER;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_INCIDENTMONITOR;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_INCIDENTMONITOR_KLADBLOK;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_TABLE;
import static nl.opengeogroep.safetymaps.server.db.DB.USER_ROLE_TABLE;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_INCIDENT_GOOGLEMAPSNAVIGATION;
import static nl.opengeogroep.safetymaps.server.db.DB.getUserDetails;
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
 * /viewer/api/organisation.json: general settings
 * /viewer/api/features.json: objects for creator objects, with ETag caching
 * /viewer/api/object/n.json: creator object details
 * /viewer/api/library.json: creator library
 * /viewer/api/autocomplete/search: see NLExtractBagAddressSearchActionBean
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/viewer/api/{path}")
public class ViewerApiActionBean implements ActionBean {
    private static final Log log = LogFactory.getLog(ViewerApiActionBean.class);

    private static final String FEATURES = "features.json";
    private static final String ORGANISATION = "organisation.json";
    private static final String STYLES = "styles.json";
    private static final String LIBRARY = "library.json";
    private static final String OBJECT = "object/";

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

    public static JSONObject getOrganisation(Connection c, int srid) throws Exception {
        Object org = new QueryRunner().query(c, "select \"organisation\" from organisation.organisation_nieuw_json(" + srid + ")", new ScalarHandler<>());
        JSONObject j = new JSONObject();
        j.put("organisation", new JSONObject(org.toString()));
        return j;
    }

    public static JSONObject getOrganisationWithUserAuthorization(final String username, Connection c, int srid) throws Exception {
        final Set<String> roles = new HashSet<String>(new QueryRunner().query(c, "select role from " + USER_ROLE_TABLE + " where username = ?", new ColumnListHandler<String>(), username));
        return getOrganisationWithAuthorizedModules(new HttpServletRequestWrapper(new MockHttpServletRequest(null, null)) {
            @Override
            public String getRemoteUser() {
                return username;
            }

            @Override
            public boolean isUserInRole(String role) {
                return roles.contains(role);
            }
        }, c, srid);
    }

    public static JSONObject getOrganisationWithAuthorizedModules(HttpServletRequest request, Connection c, int srid) throws Exception {
        Object org = new QueryRunner().query(c, "select \"organisation\" from organisation.organisation_nieuw_json(" + srid + ")", new ScalarHandler<>());
        JSONObject organisation = new JSONObject(org.toString());
        organisation.put("integrated", true);
        organisation.put("username", request.getRemoteUser());
        if(!request.isUserInRole(ROLE_ADMIN)) {
            List<Map<String,Object>> roles = new QueryRunner().query(c, "select role, modules from " + ROLE_TABLE + " where modules is not null", new MapListHandler());
            Set<String> authorizedModules = new HashSet();
            for(Map<String,Object> role: roles) {
                if(request.isUserInRole(role.get("role").toString())) {
                    String modules = (String)role.get("modules");
                    authorizedModules.addAll(Arrays.asList(modules.split(", ")));
                }
            }
            JSONArray modules = organisation.getJSONArray("modules");
            JSONArray jaAuthorizedModules = new JSONArray();
            for(int i = 0; i < modules.length(); i++) {
                JSONObject module = modules.getJSONObject(i);
                if(authorizedModules.contains(module.getString("name"))) {
                    checkModuleAuthorizations(request, c, module);
                    jaAuthorizedModules.put(module);
                }
            }
            organisation.put("modules", jaAuthorizedModules);

        } else {
            JSONArray modules = organisation.getJSONArray("modules");
            for(int i = 0; i < modules.length(); i++) {
                // Add settings to options for admin
                checkModuleAuthorizations(request, c, modules.getJSONObject(i));
            }
        }
        JSONObject j = new JSONObject();

        j.put("organisation", organisation);
        return j;
    }

    /**
     * Modify module options to apply authorizations.
     */
    private static JSONObject checkModuleAuthorizations(HttpServletRequest request, Connection c, JSONObject module) throws Exception {
        String name = module.getString("name");
        JSONObject options = module.isNull("options") ? new JSONObject(): module.getJSONObject("options");
        if("incidents".equals(name)) {
            options.put("sourceVrhAGSAuthorized", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(VrhAGSProxyActionBean.ROLE));
            options.put("sourceSafetyConnectAuthorized", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(SafetyConnectProxyActionBean.ROLE));
            options.put("incidentMonitorAuthorized", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(ROLE_INCIDENTMONITOR));
            boolean kladbklokAlwaysAuthorized = "true".equals(Cfg.getSetting("kladblok_always_authorized", "false"));
            options.put("incidentMonitorKladblokAuthorized", kladbklokAlwaysAuthorized || request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(ROLE_INCIDENTMONITOR_KLADBLOK));
            options.put("eigenVoertuignummerAuthorized", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(ROLE_EIGEN_VOERTUIGNUMMER));
            options.put("googleMapsNavigationAuthorized", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(ROLE_INCIDENT_GOOGLEMAPSNAVIGATION));

            JSONObject details = getUserDetails(request, c);
            options.put("userVoertuignummer", details.optString("voertuignummer", null));
        } else if("drawing".equals(name)) {
            options.put("editAuthorized", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(ROLE_DRAWING_EDITOR));
        }
        return module;
    }

    private Resolution organisation(Connection c) throws Exception {
        return new StreamingResolution("application/json", getOrganisationWithAuthorizedModules(getContext().getRequest(), c, srid).toString(indent));
    }

    public static JSONObject getLibrary(Connection c) throws Exception {
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
        return library;
    }

    private Resolution library(Connection c) throws Exception {
        return new StreamingResolution("application/json", getLibrary(c).toString(indent));
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
}
