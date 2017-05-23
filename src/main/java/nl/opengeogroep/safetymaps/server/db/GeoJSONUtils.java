package nl.opengeogroep.safetymaps.server.db;

import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Matthijs Laan
 */
public class GeoJSONUtils {

    public static JSONObject rowsToGeoJSONFeatureCollection(List<Map<String,Object>> rows) {
        JSONObject fc = new JSONObject();
        fc.put("type", "FeatureCollection");
        JSONArray features = new JSONArray();
        fc.put("features", features);
        for(Map<String,Object> row: rows) {
            JSONObject feature = new JSONObject();
            feature.put("type", "Feature");
            features.put(feature);
            JSONObject props = new JSONObject();
            feature.put("properties", props);
            for(Map.Entry<String,Object> column: row.entrySet()) {
                if("geometry".equals(column.getKey())) {
                    JSONObject geometry = new JSONObject((String)column.getValue());
                    feature.put(column.getKey(), geometry);
                } else {
                    if("selectiekader".equals(column.getKey()) && column.getValue() != null) {
                        props.put(column.getKey(), new JSONObject((String)column.getValue()));
                    } else {
                        props.put(column.getKey(), column.getValue());
                    }
                }
            }
        }
        return fc;
    }
}
