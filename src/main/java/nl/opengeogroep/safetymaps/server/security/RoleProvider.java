/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.server.security;

import java.util.Collection;
import java.util.Map;

/**
 *
 * @author matthijsln
 */
public interface RoleProvider {
    public Collection<String> getRoles(String username);
    public Map<String, Collection<String>> getAllRolesByUsername();
}
