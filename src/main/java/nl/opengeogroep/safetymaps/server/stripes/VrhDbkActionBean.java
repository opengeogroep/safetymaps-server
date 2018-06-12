package nl.opengeogroep.safetymaps.server.stripes;

import java.sql.Connection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.Validate;
import nl.opengeogroep.safetymaps.server.db.DB;
import static nl.opengeogroep.safetymaps.server.db.JsonExceptionUtils.logExceptionAndReturnJSONObject;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author matthijsln
 */
@StrictBinding
@UrlBinding("/api/vrh/dbk")
public class VrhDbkActionBean implements ActionBean {
    private static final Log log = LogFactory.getLog(VrhDbkActionBean.class);
    
    private ActionBeanContext context;

    @Validate
    private int indent = 0;  
    
    @Validate
    private Integer id;
    
    // <editor-fold defaultstate="collapsed" desc="getters and setters">
    public ActionBeanContext getContext() {
        return context;
    }

    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public int getIndent() {
        return indent;
    }

    public void setIndent(int indent) {
        this.indent = indent;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
    // </editor-fold>

    @DefaultHandler
    public Resolution features() {
        
        // TODO caching, controlled by shapefile import script executing query
        
        try(Connection c = DB.getConnection()) {

            JSONArray objects = new JSONArray();
            
            List<Map<String,Object>> rows = DB.qr().query("select o.id, naam, oms_nummer, straatnaam, huisnummer, huisletter, toevoeging, postcode, plaats, " +
                    "st_astext(st_centroid(o.geom)) as pand_centroid, st_astext(st_envelope(o.geom)) as extent " +
                    "from vrh.dbk_object o " +
                    "left join vrh.pand p on (p.dbk_object = o.id) " +
                    "where p.hoofd_sub='Hoofdpand'", new MapListHandler());
            
            List<Map<String,Object>> adressen = DB.qr().query("select dbk_object as id, " +
                    "        (select array_to_json(array_agg(row_to_json(r.*))) " +
                    "         from (select na.postcode as pc, na.woonplaats as pl, na.straatnaam as sn,  " +
                    "                array_to_json(array_agg(distinct na.huisnummer || case when na.huisletter <> '' or na.toevoeging <> '' then '|' || coalesce(na.huisletter,'') || '|' || coalesce(na.toevoeging,'') else '' end)) as nrs " +
                    "                from vrh.nevendbkadres na " +
                    "                where na.dbk_object = t.dbk_object " +
                    "                group by na.postcode, na.woonplaats, na.straatnaam) r " +
                    "        ) as selectieadressen " +
                    "    from (select distinct dbk_object from vrh.nevendbkadres where huisletter is not null) t", new MapListHandler());
            
            Map<Object,String> selectieadressenById = new HashMap();
            for(Map<String,Object> row: adressen) {
                selectieadressenById.put(row.get("id"), row.get("selectieadressen").toString());
            }
            
            Set objectIds = new HashSet();
        
            for(Map<String,Object> row: rows) {
                JSONObject o = new JSONObject();

                Object id = row.get("id");
                if(objectIds.contains(id)) {
                    // Duplicate hoofdpand pand row 
                    continue;
                }
                objectIds.add(id);
                
                for(String key: row.keySet()) {
                    o.put(key, row.get(key));
                }
                
                String selectieadressen = selectieadressenById.get(id);
                if(selectieadressen != null) {
                    o.put("selectieadressen", new JSONArray(selectieadressen));
                }
                objects.put(o);
            }
            
            
            JSONObject r = new JSONObject();
            r.put("success", true);
            r.put("value", objects);
            return new StreamingResolution("application/json", r.toString(indent));
        } catch(Exception e) {
            return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Error on " + getContext().getRequest().getRequestURI(), e).toString(indent));
        }
    }
    
}
