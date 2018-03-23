/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.routing;

import nl.opengeogroep.safetymaps.routing.service.OpenRouteService;
import nl.opengeogroep.safetymaps.server.db.Cfg;

/**
 *
 * @author matthijsln
 */
public class RoutingFactory {
    public static RoutingService getRoutingService() throws Exception {
        try {
            String apiKey = Cfg.getSetting("openrouteservice_apikey");
            OpenRouteService service = new OpenRouteService(apiKey);
            service.setProfile(Cfg.getSetting("openrouteservice_profile"));
            service.setPreference(Cfg.getSetting("openrouteservice_preference"));
            return service;
        } catch(Exception e) {
            throw new Exception("Error initializing routing engine: " + e.getMessage(), e);
        }
    }
}
