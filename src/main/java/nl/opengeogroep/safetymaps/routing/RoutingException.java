/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.routing;

import java.io.IOException;

/**
 *
 * @author matthijsln
 */
public class RoutingException extends IOException {
    public RoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}
