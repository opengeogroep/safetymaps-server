/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.routing;

import nl.opengeogroep.safetymaps.routing.service.GraphHopper;
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
            String url = Cfg.getSetting("graphhopper_url");
            GraphHopper service = new GraphHopper();
            service.setProfile(Cfg.getSetting("graphhopper_profile"));
            service.setURL(url);
            log.debug("Using GraphHopper engine for routing with URL " + url);
            return service;
        } catch(Exception e) {
            throw new Exception("Error initializing routing engine: " + e.getMessage(), e);
        }
    }
}
