package nl.opengeogroep.safetymaps.server.stripes;

import static nl.opengeogroep.safetymaps.server.db.JsonExceptionUtils.logExceptionAndReturnJSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.ErrorResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.StrictBinding;
import net.sourceforge.stripes.action.UrlBinding;
import net.sourceforge.stripes.validation.Validate;
import nl.opengeogroep.safetymaps.server.db.DB;

/**
 * @author Safety C&T
 */
@StrictBinding
@UrlBinding("/viewer/api/geoserver/{path}")
public class GeoserverActionBean implements ActionBean {
    private ActionBeanContext context;

    private static final Log log = LogFactory.getLog(GeoserverActionBean.class);

    @Validate
    private String path;

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

    public Resolution api() {
        try(Connection c = DB.getConnection()) {
            if(path != null) {
                String geoserverUrl = handleParams(getContext().getRequest()).replace("&path=" + path + "&url=", "");
                URL url = new URL (geoserverUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                ResultSetHandler<String> handleQuery = new ResultSetHandler<String>() {
                    public String handle(ResultSet rs) throws SQLException {
                        if (!rs.next()) {
                            return null;
                         }
                         return rs.getString(1);
                    }
                };
                String name = geoserverUrl.split("\\?", 2)[0] + "?";
                String auth = DB.qr().query("select auth from organisation.wms where url = ?", handleQuery, name);

                connection.setRequestMethod("GET");
                if (auth != null) {
                    //String encoding = Base64.getEncoder().encodeToString((auth).getBytes());
                    connection.setRequestProperty("Authorization", "Basic " + auth);
                }

                InputStream content = (InputStream)connection.getInputStream();
                Resolution res = pngStreamingResolution(content);

                return res;
            }

            return new ErrorResolution(HttpServletResponse.SC_NOT_FOUND, "Not found: " + getContext().getRequest().getRequestURI());
        } catch(Exception e) {
            return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Error on " + getContext().getRequest().getRequestURI(), e).toString(0));
        }
    }

    /**
     * Creates a png streaming resolution from a input stream
     * @param content
     * @return
     */
    private Resolution pngStreamingResolution(final InputStream content) {
        return new Resolution() {
            @Override
            public void execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
                response.setContentType("image/png");
                response.setHeader("content-disposition", "inline; filename=geoserverlayer.png");

                OutputStream out = response.getOutputStream();
                
                int c;
                while ((c = content.read()) != -1) {
                    out.write(c);
                }

                out.flush();
                out.close();
            }
        };
    }
    
    /**
     * Create a paramatized url string from params in a request
     * @param req the request
     * @return paramatized url string
     */
    private String handleParams(HttpServletRequest req) {
        String out = "";
        Enumeration<String> parameterNames = req.getParameterNames();
 
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String[] paramValues = req.getParameterValues(paramName);

            out += "&" + paramName + "=";
            for (int i = 0; i < paramValues.length; i++) {
                String paramValue = paramValues[i];
                out += paramValue;
            }
        }
 
        return out;
    }
}