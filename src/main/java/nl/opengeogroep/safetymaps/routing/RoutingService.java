/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.routing;

/**
 *
 * @author matthijsln
 */
public interface RoutingService {
    //public Map<RouteRequest,RouteResult> getMultipleRoutes(Set<RouteRequest> requests) throws RoutingException;
    public RoutingResult getRoute(RoutingRequest request) throws RoutingException;
    
}
