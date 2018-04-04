/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.routing;

import nl.opengeogroep.safetymaps.routing.service.GraphHopper;
import nl.opengeogroep.safetymaps.routing.service.OpenRouteService;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author matthijsln
 */
public class RoutingFactory {
    private static final Log log = LogFactory.getLog(RoutingFactory.class);

    public static RoutingService getRoutingService() throws Exception {
        try {
            String engine = Cfg.getSetting("routing_engine", "openrouteservice");

            if("openrouteservice".equals(engine)) {
                String apiKey = Cfg.getSetting("openrouteservice_apikey");
                OpenRouteService service = new OpenRouteService(apiKey);
                service.setProfile(Cfg.getSetting("openrouteservice_profile"));
                service.setPreference(Cfg.getSetting("openrouteservice_preference"));
                log.info("Using OpenRouteService engine for routing");
                return service;
            } else if("graphhopper".equals(engine)) {
                String url = Cfg.getSetting("graphhopper_url");
                GraphHopper service = new GraphHopper();
                service.setURL(url);
                log.info("Using GraphHopper engine for routing with URL " + url);
                return service;
            } else {
                throw new IllegalArgumentException("Invalid routing engine configured: " + engine);
            }
        } catch(Exception e) {
            throw new Exception("Error initializing routing engine: " + e.getMessage(), e);
        }
    }
}
