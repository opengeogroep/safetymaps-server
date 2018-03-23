/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.routing;

import org.json.JSONObject;

/**
 *
 * @author matthijsln
 */
public class RoutingResult {
    private boolean success;
    private String error;
    private Exception e;
    private JSONObject route;
    private double distance;
    
    public RoutingResult() {
    
    }

    public RoutingResult(boolean success, JSONObject route) {
        this.success = success;
        this.route = route;
    }

    public RoutingResult(boolean success, String error, Exception e) {
        this.success = success;
        this.error = error;
        this.e = e;
    }

    public JSONObject getRoute() {
        return route;
    }

    public void setRoute(JSONObject route) {
        this.route = route;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }
    
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Exception getE() {
        return e;
    }

    public void setE(Exception e) {
        this.e = e;
    }
}
