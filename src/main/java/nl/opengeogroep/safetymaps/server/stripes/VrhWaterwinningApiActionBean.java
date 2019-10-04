package nl.opengeogroep.safetymaps.server.stripes;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.Validate;
import nl.opengeogroep.safetymaps.routing.*;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import nl.opengeogroep.safetymaps.server.db.DB;
import static nl.opengeogroep.safetymaps.server.db.JsonExceptionUtils.logExceptionAndReturnJSONObject;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * API for waterwinning info around VRH incidents.
 * 
 * /viewer/api/vrh/waterwinning.json?x=...&amp;y=...&dist=2500&count=
 * @author matthijsln
 */
@StrictBinding
@UrlBinding("/viewer/api/vrh/waterwinning.json")
public class VrhWaterwinningApiActionBean implements ActionBean {
    private static final Log log = LogFactory.getLog(VrhWaterwinningApiActionBean.class);
    
    private static final int PRIMARY_MAX_DISTANCE = 10000;
    private static final int PRIMARY_DEFAULT_DISTANCE = 500;
    private static final int PRIMARY_MAX_COUNT = 25;
    private static final int PRIMARY_DEFAULT_COUNT = 3;
    private static final double PRIMARY_PREROUTING_COUNT_FACTOR = 3.0;
    private static final double PRIMARY_PREROUTING_DISTANCE_FACTOR = 1.0;
    private static final int SECONDARY_MAX_DISTANCE = 10000;
    private static final int SECONDARY_DEFAULT_DISTANCE = 3000;
    private static final int SECONDARY_MAX_COUNT = 25;
    private static final int SECONDARY_DEFAULT_COUNT = 3;
    private static final double SECONDARY_PREROUTING_COUNT_FACTOR = 2.0;
    private static final double SECONDARY_PREROUTING_DISTANCE_FACTOR = 1.0;
    private static final int SECONDARY_DEFAULT_MINIMUM_SPACING = 50;
    
    private ActionBeanContext context;

    @Validate
    private Double x;
    
    @Validate
    private Double y;
    
    /**
     * Distance in meters around location to search for primary waterwinning.
     */
    @Validate
    private int primaryDist = PRIMARY_DEFAULT_DISTANCE;
    
    /**
     * Max number of primary waterwinningen
     */
    @Validate 
    private int primaryCount = PRIMARY_DEFAULT_COUNT;
    
    /**
     * Distance in meters around location to search for secondary waterwinning.
     */
    @Validate
    private int secondaryDist = SECONDARY_DEFAULT_DISTANCE;

    /**
     * Max number of secondary waterwinningen
     */
    @Validate
    private int secondaryCount = SECONDARY_DEFAULT_COUNT;

    @Validate
    private int indent = 0;  
    
    @Validate
    private int srid = 28992;    
    
    @Validate
    private boolean routing = true;
    
    @Validate
    private boolean noRouteTrim = false;

    @Validate
    private Integer secondaryMinimumSpacing;

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

    public int getPrimaryDist() {
        return primaryDist;
    }

    public void setPrimaryDist(int primaryDist) {
        this.primaryDist = primaryDist;
    }

    public int getPrimaryCount() {
        return primaryCount;
    }

    public void setPrimaryCount(int primaryCount) {
        this.primaryCount = primaryCount;
    }

    public int getSecondaryDist() {
        return secondaryDist;
    }

    public void setSecondaryDist(int secondaryDist) {
        this.secondaryDist = secondaryDist;
    }

    public int getSecondaryCount() {
        return secondaryCount;
    }

    public void setSecondaryCount(int secondaryCount) {
        this.secondaryCount = secondaryCount;
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

    public boolean isRouting() {
        return routing;
    }

    public void setRouting(boolean routing) {
        this.routing = routing;
    }

    public boolean isNoRouteTrim() {
        return noRouteTrim;
    }

    public void setNoRouteTrim(boolean noRouteTrim) {
        this.noRouteTrim = noRouteTrim;
    }

    public Integer getSecondaryMinimumSpacing() {
        return secondaryMinimumSpacing;
    }

    public void setSecondaryMinimumSpacing(Integer secondaryMinimumSpacing) {
        this.secondaryMinimumSpacing = secondaryMinimumSpacing;
    }
    // </editor-fold>
    
    @DefaultHandler
    public Resolution waterwinning() {
        try(Connection c = DB.getConnection()) {
            JSONObject waterwinningInfo;
            boolean retryCachedPlanChange = false;
            do {
                try {
                    waterwinningInfo = findWaterwinning();
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
            r.put("value", waterwinningInfo);
            return new StreamingResolution("application/json", r.toString(indent));
        } catch(Exception e) {
            return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Error on " + getContext().getRequest().getRequestURI(), e).toString(indent));
        }
    }
    
    private JSONArray findPrimaryWaterwinning(double x, double y, int srid, int distance, int count) throws Exception {
        List<Map<String,Object>> rows = DB.qr().query("select st_distance(b.geom, st_setsrid(st_point(?, ?),?)) as distance, st_x(geom) as x, st_y(geom) as y, * "
                + "from "
                + " (select geom, 'brandkranen_eigen_terrein' as tabel, \"type\", 'Voordruk aanwezig: ' || coalesce(initcap(voordruk),'Niet bekend') || coalesce(', ' || bar || ' bar', '') as info from vrh.brandkranen_eigen_terrein where lower(\"type\") <> 'afsluiter omloopleiding' "
                + "  union all "
                + "  select geom, 'brandkranen_dunea' as tabel, lower(producttyp) || case when lower(verbinding) = 'draadstuk' then ' schroef' else '' end as \"type\", '' as info from vrh.brandkranen_dunea "
                + "  union all "
                + "  select geom, 'brandkranen_evides' as tabel, case when id_brandkr = 'BB' then 'bovengronds' else 'ondergronds' end as \"type\", '' as info from vrh.brandkranen_evides "
                + "  union all "
                + "  select geom, 'brandkranen_oasen' as tabel, case when lower(ondergrnds) = 'nee' then 'bovengronds' else 'ondergronds' end as \"type\", '' as info from vrh.brandkranen_oasen "
                + "  union all "
                + "  select geom, 'geboorde_putten' as tabel, 'geboorde_put' as \"type\", overige_in as info from vrh.geboorde_putten) b "
                + "where st_distance(b.geom, st_setsrid(st_point(?, ?),?)) < ? "
                + "order by 1 asc limit ?", new MapListHandler(), x, y, srid, x, y, srid, distance, count);
        
        log.info("Waterwinning primary results " + getContext().getRequest().getRequestURI() + "?" + getContext().getRequest().getQueryString() + ": " + rows);

        JSONArray a = new JSONArray();
        for(Map<String,Object> row: rows) {
            JSONObject o = new JSONObject();

            for(String key: row.keySet()) {
                if(!"geom".equals(key)) {
                    o.put(key, row.get(key));
                }
            }
            if("brandkranen_dunea".equals(row.get("tabel"))) {
                Integer diameter = DB.qr().query("select artikel_d2::integer from vrh.hoofdleidingen_dunea order by st_distance(geom, st_setsrid(st_point(?, ?),?)) limit 1", new ScalarHandler<Integer>(), row.get("x"), row.get("y"), srid);
                log.info(String.format("Leiding voor Dunea brandkraan op %s, %s is %s", row.get("x"), row.get("y"), diameter));
                int opbrengst = 500;
                if(diameter > 90) {
                    opbrengst = 1000;
                }
                if(diameter > 190) {
                    opbrengst = 1500;
                }
                o.put("info", "&plusmn; " + opbrengst + " &#8467;/min");
            }
            a.put(o);
        }
        return a;
    }
    
    private JSONArray findSecondaryWaterwinning(double x, double y, int srid, int distance, int count, int minSpacing) throws Exception {
        
        List<Map<String,Object>> rows = DB.qr().query("select st_distance(b.geom, st_setsrid(st_point(?, ?),?)) as distance, st_x(point) as x, st_y(point) as y, type, info "
                + "from "
                + " (select geom, st_closestpoint(geom, st_setsrid(st_point(?, ?), ?)) as point, 'open_water' as \"type\", '' as info from vrh.openwater_vlakken "
                + "  union all "
                + "  select geom, geom as point, 'bluswaterriool' as \"type\", overige_in as info from vrh.bluswaterriool) b "
                + " where st_distance(b.geom, st_setsrid(st_point(?, ?), ?)) < ? "
                + " order by 1 asc limit ?", new MapListHandler(), x, y, srid, x, y, srid, x, y, srid, distance, count);

        log.info("Waterwinning secondary results " + getContext().getRequest().getRequestURI() + "?" + getContext().getRequest().getQueryString() + ": " + rows);

        JSONArray a = new JSONArray();
        for(Map<String,Object> row: rows) {
            JSONObject o = new JSONObject();
            for(String key: row.keySet()) {
                o.put(key, row.get(key));
            }
            double px = o.getDouble("x");
            double py = o.getDouble("y");
            if(minSpacing > 0 && a.length() > 0) {
                boolean tooClose = false;
                for(int i = 0; i < a.length(); i++) {
                    double ex = a.getJSONObject(i).getDouble("x");
                    double ey = a.getJSONObject(i).getDouble("y");
                    double distanceFromPoint = Math.hypot(px-ex, py-ey);
                    String msg = String.format("Secondary waterwinning point at %f, %f is %f m from existing point #%d at %f, %f",
                        px, py, distanceFromPoint, i, ex, ey);
                    if(distanceFromPoint < (double)minSpacing) {
                        log.info(msg + ", too close according to minimum spacing limit " + minSpacing + "m, excluding from result");
                        tooClose = true;
                        break;
                    }
                }
                if(!tooClose) {
                    a.put(o);
                }
            } else {
                a.put(o);
            }
        }
        return a;
    }
    
    private JSONArray calculateRoutes(JSONArray input) throws Exception {
        RoutingService engine = RoutingFactory.getRoutingService();
        
        for(int i = 0; i < input.length(); i++) {
            JSONObject wwPunt = input.getJSONObject(i);
            double toX = wwPunt.getDouble("x");
            double toY = wwPunt.getDouble("y");
            RoutingResult result = engine.getRoute(new RoutingRequest(x, y, toX, toY, srid));
            JSONObject route = new JSONObject();
            wwPunt.put("route", route);
            if(result.isSuccess()) {
                route.put("success", true);
                route.put("distance", result.getDistance());
                route.put("data", result.getRoute());
            } else {
                route.put("success", false);
                route.put("error", result.getError());
            }
        }
        return input;
    }
    
    private JSONArray sortAndTrimRouted(JSONArray input, int maxDistance, int maxCount) throws Exception {
        List<JSONObject> l = new ArrayList(input.length());
        for(int i = 0; i < input.length(); i++) {
            JSONObject o = input.getJSONObject(i);
            double distance = o.getDouble("distance");
            if(o.getJSONObject("route").getBoolean("success")) {
                distance = o.getJSONObject("route").getDouble("distance");
            }
            if(Math.floor(distance) <= maxDistance) {
                l.add(o);
            } else {
                log.debug("Throwing out point because routed distance farther than limit of " + maxDistance + ": " + o);
            }
        }
        log.debug("Sorting on routing distance and trimming to max " + maxCount + " items: " + input.toString());
        Collections.sort(l, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject lhs, JSONObject rhs) {
                double ld = lhs.getDouble("distance");
                JSONObject r = lhs.getJSONObject("route");
                if(r.getBoolean("success")) {
                    ld = r.getDouble("distance");
                }
                double rd = rhs.getDouble("distance");
                r = rhs.getJSONObject("route");
                if(r.getBoolean("success")) {
                    rd = r.getDouble("distance");
                }
                return ld - rd < 0.0 ? -1 : 1;
            }
        });
        JSONArray result = new JSONArray();
        for(int i = 0; i < l.size () && i < maxCount; i++) {
            result.put(l.get(i));
        }
        log.debug("Sorted on routing distance and trimmed to max " + maxCount + " items: " + result.toString());
        return result;
    }

    private JSONObject findWaterwinning() throws Exception {
        JSONObject ww = new JSONObject();
        if(x == null || y == null) {
            return ww;
        }

        primaryDist = Math.min(primaryDist, PRIMARY_MAX_DISTANCE);
        primaryCount = Math.min(primaryCount, PRIMARY_MAX_COUNT);
        secondaryDist = Math.min(secondaryDist, SECONDARY_MAX_DISTANCE);
        secondaryCount = Math.min(secondaryCount, SECONDARY_MAX_COUNT);
        
        if(secondaryMinimumSpacing == null) {
            secondaryMinimumSpacing = SECONDARY_DEFAULT_MINIMUM_SPACING;
        }

        if(!routing) {
            ww.put("primary", findPrimaryWaterwinning(x, y, srid, primaryDist, primaryCount));
            ww.put("secondary", findSecondaryWaterwinning(x, y, srid, secondaryDist, secondaryCount, secondaryMinimumSpacing));
        } else {
            double primaryDistanceFactor = Double.parseDouble(Cfg.getSetting("ww_prerouting_primary_distance_factor", PRIMARY_PREROUTING_DISTANCE_FACTOR + ""));
            double primaryCountFactor = Double.parseDouble(Cfg.getSetting("ww_prerouting_primary_count_factor", PRIMARY_PREROUTING_COUNT_FACTOR + ""));
            
            JSONArray routingInput = findPrimaryWaterwinning(x, y, srid, (int)Math.ceil(primaryDist * primaryDistanceFactor), (int)Math.ceil(primaryCount * primaryCountFactor));
            JSONArray routed = calculateRoutes(routingInput);
            if(!noRouteTrim) {
                routed = sortAndTrimRouted(routed, primaryDist, primaryCount);
            }
            ww.put("primary", routed);
            
            double secondaryDistanceFactor = Double.parseDouble(Cfg.getSetting("ww_prerouting_secondary_distance_factor", SECONDARY_PREROUTING_DISTANCE_FACTOR + ""));
            double secondaryCountFactor = Double.parseDouble(Cfg.getSetting("ww_prerouting_secondary_count_factor", SECONDARY_PREROUTING_COUNT_FACTOR + ""));

            routingInput = findSecondaryWaterwinning(x, y, srid, (int)Math.ceil(secondaryDist * secondaryDistanceFactor), (int)Math.ceil(secondaryCount * secondaryCountFactor), secondaryMinimumSpacing);
            routed = calculateRoutes(routingInput);
            if(!noRouteTrim) {
                routed = sortAndTrimRouted(routed, secondaryDist, secondaryCount);
            }
            ww.put("secondary", routed);
            
        }

        return ww;
    }
    
}
