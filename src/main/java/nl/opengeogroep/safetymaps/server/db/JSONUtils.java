package nl.opengeogroep.safetymaps.server.db;

import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author matthijsln
 */
public class JSONUtils {
    public static JSONObject rowToJson(Map<String, Object> row, boolean skipNull, boolean skipEmptyString) throws Exception {
        JSONObject o = new JSONObject();
        for(Map.Entry<String,Object> e: row.entrySet()) {
            /* do not put null or empty string properties in result */

            if(e.getValue() == null) {
                if(!skipNull) {
                    o.put(e.getKey(), (Object)null);
                }
                continue;
            }

            if("".equals(e.getValue())) {
                if(!skipEmptyString) {
                    o.put(e.getKey(), "");
                }
                continue;
            }

            /* JSON objects are returned as org.postgresql.util.PGobject,
             * compare classname by string ignoring package
             */
            if(e.getValue().getClass().getName().endsWith("PGobject")) {
                try {
                    String json = e.getValue().toString();
                    Object pgj;
                    if(json.startsWith("[")) {
                        pgj = new JSONArray(json);
                    } else if(json.startsWith("{")) {
                        pgj = new JSONObject(json);
                    } else {
                        // Just the toString()
                        pgj = json;
                    }
                    o.put(e.getKey(), pgj);
                } catch(JSONException ex) {
                    throw new Exception("Error parsing PostgreSQL JSON for property " + e.getKey() + ": " + ex.getMessage() + ", JSON=" + e.getValue());
                }
            } else {
                o.put(e.getKey(), e.getValue());
            }
        }
        return o;
    }
}
