/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.server.stripes;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.Validate;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.DB;
import static nl.opengeogroep.safetymaps.server.db.JSONUtils.rowToJson;
import static nl.opengeogroep.safetymaps.server.db.JsonExceptionUtils.logExceptionAndReturnJSONObject;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;

/**
 *
 * @author matthijsln
 */
@StrictBinding
@UrlBinding("/viewer/api/autocomplete/{term}")
public class NLExtractBagAddressSearchActionBean  implements ActionBean {
    private static final Log log = LogFactory.getLog(NLExtractBagAddressSearchActionBean.class);
    private ActionBeanContext context;

    @Validate
    private String term;
    
    public ActionBeanContext getContext() {
        return context;
    }

    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public Resolution search() {
        try {
            JSONArray result = new JSONArray();

            if(term != null && term.trim().length() > 2) {
                term = term.trim().toLowerCase();
                
                QueryRunner qr = DB.bagQr();
                
                String where;
                String param;
                
                boolean isPostCode = term.matches("^[0-9]{4}[a-z]{0,2}$");
                if(term.indexOf(' ') != -1 || isPostCode) {
                    where = "(textsearchable_adres @@ to_tsquery('dutch',?)) ";
                    param = term.replaceAll("\\s+", ":*&") + ":*";                    
                } else {
                    where = "openbareruimtenaam like ?";
                    param = term.substring(0, 1).toUpperCase() + term.substring(1) + "%";
                }
                
                List<Map<String,Object>> rows = qr.query("select openbareruimtenaam || ' ' || " +
                    "CASE WHEN lower(woonplaatsnaam) = lower(gemeentenaam) THEN woonplaatsnaam " +
                    "ELSE woonplaatsnaam || ', ' || gemeentenaam END as display_name, " +
                    "st_x(st_centroid(st_collect(geopunt))) as lon, " +
                    "st_y(st_centroid(st_collect(geopunt))) as lat " +
                    "from bag_actueel.adres where " +
                    where +
                    "group by woonplaatsnaam, gemeentenaam, openbareruimtenaam limit 10", new MapListHandler(), param);
                
                if(rows.size() == 1) {
                    // Only one grouped by result, get more details
                    
                    rows = qr.query("select openbareruimtenaam || ' ' || " +
                        "COALESCE(CAST(huisnummer as varchar) || ' ','') || " +
                        "COALESCE(CAST(huisletter as varchar) || ' ','') || " +
                        "COALESCE(CAST(huisnummertoevoeging as varchar) || ' ','') || " +
                        "COALESCE(CAST(postcode as varchar) || ' ','') || " +                            
                        "CASE WHEN lower(woonplaatsnaam) = lower(gemeentenaam) THEN woonplaatsnaam " +
                        "ELSE woonplaatsnaam || ', ' || gemeentenaam END as display_name, " +
                        "st_x(st_centroid(st_collect(geopunt))) as lon, " +
                        "st_y(st_centroid(st_collect(geopunt))) as lat " +
                        "from bag_actueel.adres where " +
                        where +
                        "group by woonplaatsnaam, gemeentenaam, openbareruimtenaam, huisnummer, huisletter, huisnummertoevoeging, postcode limit 10", new MapListHandler(), param);                    
                }
               
                for(Map<String,Object> row: rows) {
                    result.put(rowToJson(row, false, false));
                }
            }

            return new StreamingResolution("application/json", new StringReader(result.toString(4)));
        } catch(Exception e) {
            return new ErrorMessageResolution(logExceptionAndReturnJSONObject(log, "Error searching BAG database for address", e).toString(4));
            
        }
    }
    
    public static void main(String[] args) {
        String[] terms = new String[] {
            "1",
            "12",
            "123",
            "1234",
            "3542",
            "3542e",
            "3542ec straat",
            "1234abc" 
        };
        for(String t: terms) {
            System.out.println(t + ": " + t.matches("^[0-9]{4}[a-z]{0,2}$"));
        }
    }
    
}
