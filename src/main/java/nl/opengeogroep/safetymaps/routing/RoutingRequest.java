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
public class RoutingRequest {
    private double fromX, fromY, toX, toY;
    private int srid;

    public RoutingRequest(double fromX, double fromY, double toX, double toY, int srid) {
        this.fromX = fromX;
        this.fromY = fromY;
        this.toX = toX;
        this.toY = toY;
        this.srid = srid;
    }

    public double getFromX() {
        return fromX;
    }

    public void setFromX(double fromX) {
        this.fromX = fromX;
    }

    public double getFromY() {
        return fromY;
    }

    public void setFromY(double fromY) {
        this.fromY = fromY;
    }

    public double getToX() {
        return toX;
    }

    public void setToX(double toX) {
        this.toX = toX;
    }

    public double getToY() {
        return toY;
    }

    public void setToY(double toY) {
        this.toY = toY;
    }

    public int getSrid() {
        return srid;
    }

    public void setSrid(int srid) {
        this.srid = srid;
    }
    
    
}
