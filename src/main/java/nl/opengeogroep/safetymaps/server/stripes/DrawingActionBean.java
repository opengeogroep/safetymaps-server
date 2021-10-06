package nl.opengeogroep.safetymaps.server.stripes;

import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.Validate;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.DB;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_ADMIN;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_DRAWING_EDITOR;
import static nl.opengeogroep.safetymaps.server.security.CorsFilter.addCorsHeaders;

/**
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/viewer/api/drawing/{incident}.json")
public class DrawingActionBean  implements ActionBean {
    private static final Log log = LogFactory.getLog(DrawingActionBean.class);

    private ActionBeanContext context;

    private class DrawingCache {
        Date lastModified;
        String modifiedBy;

        public DrawingCache(Date lastModified, String modifiedBy) {
            this.lastModified = lastModified;
            this.modifiedBy = modifiedBy;
        }
    }

    private static final Map<String,DrawingCache> drawingCache = new HashMap();

    private static final int FEATURES_CACHE_LIMIT = 10;

    private static final Map<String,String> drawingFeatureCache = new HashMap();

    @Validate
    private String incident;

    @Validate
    private String features;

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public String getIncident() {
        return incident;
    }

    public void setIncident(String incident) {
        this.incident = incident;
    }

    public String getFeatures() {
        return features;
    }

    public void setFeatures(String features) {
        this.features = features;
    }

    @DefaultHandler
    public Resolution defaultHandler() throws Exception {

        if("POST".equals(context.getRequest().getMethod())) {
            return save();
        } else {
            return load();
        }
    }

    public Resolution load() throws Exception {

        // TODO check user is admin or has drawing module authorized, need to
        // cache db query results for that, refresh after timeout

        DrawingCache dc = null;
        String drawingFeatures = null;

        try {
            synchronized(drawingCache) {
                if(!drawingCache.containsKey(incident)) {
                    if(!loadDrawingInfo()) {
                        return new ErrorMessageResolution(SC_NOT_FOUND, "Drawing for incident " + incident + " not found");
                    }
                }
                dc = drawingCache.get(incident);

                long ifModifiedSince = getContext().getRequest().getDateHeader("If-Modified-Since") / 1000;

                if(ifModifiedSince >= dc.lastModified.getTime() / 1000) {
                    return new ErrorResolution(HttpServletResponse.SC_NOT_MODIFIED);
                }

                drawingFeatures = getDrawingFeatures();
            }
        } catch(Exception e) {
            log.error("Error loading drawing", e);
            return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error: " + e.getClass() + ": " + e.getMessage());
        }

        final DrawingCache fDc = dc;
        final String fFeatures = drawingFeatures;

        return new Resolution() {
            @Override
            public void execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
                String encoding = "UTF-8";
                response.setCharacterEncoding(encoding);
                response.setContentType("application/json");
                response.addDateHeader("Last-Modified", fDc.lastModified.getTime());
                response.addHeader("Cache-Control", "must-revalidate");

                OutputStream out;
                String acceptEncoding = request.getHeader("Accept-Encoding");
                if(acceptEncoding != null && acceptEncoding.contains("gzip")) {
                    response.setHeader("Content-Encoding", "gzip");
                    out = new GZIPOutputStream(response.getOutputStream(), true);
                } else {
                    out = response.getOutputStream();
                }
                IOUtils.copy(new StringReader(fFeatures), out, encoding);
                out.flush();
                out.close();
            }
        };
    }

    /**
     * Loads drawing metadata (no features). Must be called from within a synchronized(drawingCache) block
     */
    private boolean loadDrawingInfo() throws Exception {
        Map results = DB.qr().query("select last_modified, modified_by from safetymaps.drawing where incident = ?", new MapHandler(), incident);

        if(results == null) {
            return false;
        }

        drawingCache.put(incident, new DrawingCache((Date)results.get("last_modified"), (String)results.get("modified_by")));
        return true;
    }

    /**
     * Must be called from within a synchronized(drawingCache) block
     */
    private DrawingCache saveNewDrawing() throws Exception {

        DrawingCache dc = new DrawingCache(new Date(), getContext().getRequest().getRemoteUser());
        DB.qr().update("insert into safetymaps.drawing(incident, features, last_modified, modified_by) values (?, ?, ?, ?)",
                incident, features, new Timestamp(dc.lastModified.getTime()), dc.modifiedBy);

        drawingCache.put(incident, dc);
        drawingFeatureCache.put(incident, features);

        return dc;
    }

    private DrawingCache updateDrawing() throws Exception {
        DrawingCache dc = new DrawingCache(new Date(), getContext().getRequest().getRemoteUser());
        DB.qr().update("update safetymaps.drawing set features = ?, last_modified = ?, modified_by = ? where incident = ?",
                features, new Timestamp(dc.lastModified.getTime()), dc.modifiedBy, incident);

        drawingCache.put(incident, dc);
        drawingFeatureCache.put(incident, features);
        return dc;
    }

    private void cleanupFeaturesCache() {
        if(drawingFeatureCache.size() >= FEATURES_CACHE_LIMIT) {
            // Don't do any smart FIFO or anything, just clear cache when it
            // reaches the limit.
            drawingFeatureCache.clear();
        }
    }

    /**
     * Must be called from within a synchronized(drawingCache) block
     */
    private String getDrawingFeatures() throws Exception {

        cleanupFeaturesCache();

        String theFeatures = DB.qr().query("select features from safetymaps.drawing where incident = ?", new ScalarHandler<String>(), incident);
        drawingFeatureCache.put(incident, theFeatures);
        return theFeatures;
    }

    public Resolution save() throws Exception {
        HttpServletRequest request = getContext().getRequest();

        // TODO must re-check database, using cached info
        if(!request.isUserInRole(ROLE_ADMIN) && !request.isUserInRole(ROLE_DRAWING_EDITOR) && !request.isUserInRole("smvng_drawing_crud")) {
            return new ErrorResolution(HttpServletResponse.SC_FORBIDDEN);
        }

        DrawingCache dc = null;
        String drawingFeatures = null;

        try {
            synchronized(drawingCache) {
                if(!drawingCache.containsKey(incident)) {
                    dc = saveNewDrawing();
                    return new ErrorMessageResolution(HttpServletResponse.SC_CREATED, "").setLastModified(dc.lastModified.getTime());
                }

                dc = drawingCache.get(incident);

                long ifUnmodifiedSince = getContext().getRequest().getDateHeader("If-Unmodified-Since") / 1000;

                if(ifUnmodifiedSince > 0 && ifUnmodifiedSince < dc.lastModified.getTime() / 1000) {
                    JSONObject j = new JSONObject();
                    j.put("lastModified", dc.lastModified.getTime() / 1000);
                    j.put("modifiedBy", dc.modifiedBy);
                    return new ErrorMessageResolution(HttpServletResponse.SC_PRECONDITION_FAILED, j.toString()) {
                        @Override
                        protected void stream(HttpServletResponse response) throws Exception {
                            response.setContentType("application/json");
                            super.stream(response);
                        }
                    };
                }

                dc = updateDrawing();
                return new ErrorMessageResolution(HttpServletResponse.SC_OK, "").setLastModified(dc.lastModified.getTime());
            }

        } catch(Exception e) {
            log.error("Error saving drawing", e);
            return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error: " + e.getClass() + ": " + e.getMessage());
        }
    }
}
