package nl.opengeogroep.safetymaps.server.stripes;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
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
 * API for waterwinning info around VRH incidents.
 * 
 * /api/vrh/waterwinning.json?x=...&amp;y=...&dist=2500&count=
 * @author matthijsln
 */
@StrictBinding
@UrlBinding("/api/vrh/waterwinning.json")
public class VrhWaterwinningApiActionBean implements ActionBean {
    private static final Log log = LogFactory.getLog(ViewerApiActionBean.class);
    
    private static final int MAX_DISTANCE = 10000;
    private static final int DEFAULT_DISTANCE = 2500;
    private static final int MAX_COUNT = 25;
    private static final int DEFAULT_COUNT = 3;
    
    private ActionBeanContext context;

    @Validate
    private Double x;
    
    @Validate
    private Double y;
    
    /**
     * Distance in meters around location to search for waterwinning.
     */
    @Validate
    private int dist = DEFAULT_DISTANCE;
    
    /**
     * Max count of waterwinningen per type
     */
    @Validate 
    private int count = DEFAULT_COUNT;
    
    @Validate
    private int indent = 0;  
    
    @Validate
    private int srid = 28992;    
    
    // <editor-fold defaultstate="collapsed" desc="getters and setters">
    @Override
    public ActionBeanContext getContext() {
        return context;
    }
    
    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }
    
    public Double getX() {
        return x;
    }
    
    public void setX(Double x) {
        this.x = x;
    }
    
    public Double getY() {
        return y;
    }
    
    public void setY(Double y) {
        this.y = y;
    }
    
    public int getDist() {
        return dist;
    }
    
    public void setDist(int dist) {
        this.dist = dist;
    }
    
    public int getCount() {
        return count;
    }
    
    public void setCount(int count) {
        this.count = count;
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
    // </editor-fold>
    
    @DefaultHandler
    public Resolution waterwinning() {
        try(Connection c = DB.getConnection()) {
            JSONArray waterwinningen;
            boolean retryCachedPlanChange = false;
            do {
                try {
                    waterwinningen = findWaterwinning();
                } catch(SQLException sqle) {
                    String ss = sqle.getSQLState();
                    //if(ss.equals(/* INSERT CORRECT SQL STATE HERE */"Cached plan must not change result type") && !retryCachedDDLChange) {
                    //    log.warn("Got PSQL error that cached plan changed result type, retrying once for DDL changes after DEALLOCATE ALL");
                    //    DB.qr().update("DEALLOCATE ALL");
                    //    retryCachedPlanChange = true;
                    //} else {
                    //    log.error("Got PSQL error that cached plan changed result type but already retried once; rethrowing exception");
                    //    throw sqle;
                    //}
                    throw sqle;
                }
            } while(retryCachedPlanChange);
            
            JSONObject r = new JSONObject();
            r.put("success", true);
            r.put("values", waterwinningen);
            return new StreamingResolution("application/json", r.toString(indent));
        } catch(Exception e) {
            return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Error on " + getContext().getRequest().getRequestURI(), e).toString(indent));
        }
    }
    
    private JSONArray findWaterwinning() throws Exception {
        JSONArray ww = new JSONArray();
        if(x == null || y == null) {
            return ww;
        }

        dist = Math.min(dist, MAX_DISTANCE);
        count = Math.min(count, MAX_COUNT);

        List<Map<String,Object>> rows = DB.qr().query("select st_distance(geom, st_setsrid(st_point(?, ?),?)) as distance, st_x(geom) as x, st_y(geom) as y, * "
                + "from "
                + " (select geom, 'brandkranen_eigen_terrein' as tabel, \"type\", 'NB' as info from vrh.brandkranen_eigen_terrein "
                + "  union all "
                + "  select geom, 'brandkranen_dunea' as tabel, lower(producttyp) as \"type\", 'NB' as info from vrh.brandkranen_dunea "
                + "  union all "
                + "  select geom, 'brandkranen_evides' as tabel, 'ondergronds' as \"type\", 'Nominale druk: ' || nominale_d as info from vrh.brandkranen_evides "
                + "  union all "
                + "  select geom, 'brandkranen_oasen' as tabel, case when lower(ondergrnds) = 'nee' then 'bovengronds' else 'ondergronds' end as \"type\", omschr_lok as info from vrh.brandkranen_oasen) b "
                + "where st_distance(geom, st_setsrid(st_point(?, ?),?)) < ? "
                + "order by 1 asc limit ?", new MapListHandler(), x, y, srid, x, y, srid, dist, count);
        
        log.info("Waterwinning results " + getContext().getRequest().getRequestURI() + "?" + getContext().getRequest().getQueryString() + ": " + rows);

        for(Map<String,Object> row: rows) {
            JSONObject o = new JSONObject();
            for(String key: row.keySet()) {
                if(!"geom".equals(key)) {
                    o.put(key, row.get(key));
                }
            }
            ww.put(o);
        }
        
        return ww;
    }
    
}
