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
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import static nl.opengeogroep.safetymaps.server.db.JsonExceptionUtils.*;
import nl.opengeogroep.safetymaps.server.db.DB;

/**
 * @author bartv
 */
@UrlBinding("/viewer/api/oiv/{path}")
public class OivActionBean implements ActionBean {
  private static final Log log = LogFactory.getLog(OivActionBean.class);
  private ActionBeanContext context;

  private String id;

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

  @DefaultHandler 
  public Resolution oiv() {
    if(isNotAuthorized()) {
      return new ErrorMessageResolution(HttpServletResponse.SC_FORBIDDEN, "Gebruiker heeft geen toegang tot /api/oiv");
    }

    try {
      if(id != null) {
        return getObjectJson();
      } else {
        return getFeaturesJson();
      }
    } catch(Exception e) {
      return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Error on /api/oiv", e).toString());
    }
  }

  private Boolean isNotAuthorized() {
    return false;
  } 

  private Resolution getFeaturesJson() {    
    try {
      JSONObject o = new JSONObject("{ success:true }");
      o.put("results", getFeaturesFromDatabase());

      return new Resolution() {
        @Override
        public void execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
          String encoding = "UTF-8";
          OutputStream out;
          String acceptEncoding = request.getHeader("Accept-Encoding");

          response.setCharacterEncoding(encoding);
          response.setContentType("application/json");

          if(acceptEncoding != null && acceptEncoding.contains("gzip")) {
              response.setHeader("Content-Encoding", "gzip");
              out = new GZIPOutputStream(response.getOutputStream(), true);
          } else {
              out = response.getOutputStream();
          }

          IOUtils.copy(new StringReader(o.toString()), out, encoding);
          out.flush();
          out.close();
        }
      };
    } catch(Exception e) {
      return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Error getting oiv features", e).toString());
    }
  }

  private Resolution getObjectJson() {
    try {
      JSONObject o = getObjectFromDatabase();

      return new Resolution() {
        @Override
        public void execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
          String encoding = "UTF-8";
          OutputStream out;
          String acceptEncoding = request.getHeader("Accept-Encoding");

          response.setCharacterEncoding(encoding);
          response.setContentType("application/json");

          if(acceptEncoding != null && acceptEncoding.contains("gzip")) {
              response.setHeader("Content-Encoding", "gzip");
              out = new GZIPOutputStream(response.getOutputStream(), true);
          } else {
              out = response.getOutputStream();
          }

          IOUtils.copy(new StringReader(o.toString()), out, encoding);
          out.flush();
          out.close();
        }
      };
    } catch(Exception e) {
      return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Error getting oiv object", e).toString());
    }
  }

  private JSONArray getFeaturesFromDatabase() throws Exception {
    QueryRunner qr = DB.oivQr();
    String sql = "select * from vv_objecten_list";
    List<Map<String, Object>> rows = qr.query(sql, new MapListHandler());
    JSONArray results = new JSONArray();

    for (Map<String, Object> row : rows) {
      results.put(new JSONObject(row.get("row_to_json")));
    }

    return results;
  }

  private JSONObject getObjectFromDatabase() throws Exception {
    QueryRunner qr = DB.oivQr();
    String sql = "select * from objecten.vv_objecten where id || '_bouwlaag_' || bouwlaag = ?";
    Object[] qparams;
    
    qparams = new Object[] {
      id
    };

    List<Map<String, Object>> rows = qr.query(sql, new MapListHandler(), qparams);

    if(rows.isEmpty()) {
      return null;
    } else {
      return new JSONObject(rows.get(0));
    }
  }
}