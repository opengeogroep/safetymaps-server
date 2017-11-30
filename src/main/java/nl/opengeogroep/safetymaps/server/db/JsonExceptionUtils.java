package nl.opengeogroep.safetymaps.server.db;

import org.apache.commons.logging.Log;
import org.json.JSONObject;

/**
 *
 * @author Matthijs Laan
 */
public class JsonExceptionUtils {
    public static JSONObject logExceptionAndReturnJSONObject(Log log,  String msg, Exception e) {
        log.error(msg, e);
        JSONObject o = new JSONObject();
        o.put("success", false);
        msg = msg + ": " + e.getClass() + ": " + e.getMessage();
        if(e.getCause() != null) {
            msg = msg + ", cause: " + e.getCause().getClass() + ": " + e.getCause().getMessage();
        }
        o.put("error", msg);
        return o;
    }
}
