/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.server.security;

import nl.opengeogroep.safetymaps.server.db.DB;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static nl.opengeogroep.safetymaps.server.db.DB.qr;

/**
 *
 * @author matthijsln
 */
public class SafetyMapsRoleProvider implements RoleProvider {

    @Override
    public Collection<String> getRoles(String username) {
        try {
            return qr().query("select role from " + DB.USER_ROLE_TABLE + " where username = ?", new ColumnListHandler<String>(), username);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, Collection<String>> getAllRolesByUsername() {
        try {
            List<Map<String,Object>> rows = qr().query("select username, role from " + DB.USER_ROLE_TABLE, new MapListHandler());

            Map<String,Collection<String>> rolesByUsername = new HashMap();

            for(Map<String,Object> row: rows) {
                String username = (String)row.get("username");
                String role = (String)row.get("role");
                Collection<String> roles = rolesByUsername.get(username);
                if(roles == null) {
                    roles = new HashSet();
                    rolesByUsername.put(username, roles);
                }
                roles.add(role);
            }
            return rolesByUsername;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
