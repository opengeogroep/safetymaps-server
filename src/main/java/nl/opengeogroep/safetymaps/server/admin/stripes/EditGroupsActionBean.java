/*
 * Copyright (C) 2019 B3Partners B.V.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.naming.NamingException;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.*;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_ADMIN;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_TABLE;
import static nl.opengeogroep.safetymaps.server.db.DB.USER_ROLE_TABLE;
import static nl.opengeogroep.safetymaps.server.db.DB.qr;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author matthijsln
 */
@StrictBinding
@UrlBinding("/admin/action/groups")
public class EditGroupsActionBean implements ActionBean, ValidationErrorHandler {
    private static final Log log = LogFactory.getLog("admin.groups");

    private static final String JSP = "/WEB-INF/jsp/admin/editGroups.jsp";

    private ActionBeanContext context;

    private List<Map<String,Object>> allRoles;
    private List<Map<String,Object>> allModules;

    @Validate(required = true, on = {"save", "delete"})
    private String role;

    @Validate
    List<String> modules = new ArrayList<>();

    // <editor-fold defaultstate="collapsed" desc="getters and setters">
    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public List<Map<String, Object>> getAllModules() {
        return allModules;
    }

    public void setAllModules(List<Map<String, Object>> allModules) {
        this.allModules = allModules;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<Map<String, Object>> getAllRoles() {
        return allRoles;
    }

    public void setAllRoles(List<Map<String, Object>> allRoles) {
        this.allRoles = allRoles;
    }

    public List<String> getModules() {
        return modules;
    }

    public void setModules(List<String> modules) {
        this.modules = modules;
    }
    // </editor-fold>

    @Before
    private void loadInfo() throws NamingException, SQLException {
        allRoles = qr().query("select * from " + ROLE_TABLE + " order by protected desc, role", new MapListHandler());

        allModules = qr().query("select name, enabled from organisation.modules order by 1", new MapListHandler());
    }

    @Override
    public Resolution handleValidationErrors(ValidationErrors errors) throws Exception {
        loadInfo();
        return new ForwardResolution(JSP);
    }

    @DefaultHandler
    @DontValidate
    public Resolution list() {
        return new ForwardResolution(JSP);
    }

    public Resolution edit() throws SQLException, NamingException {
        String s = qr().query("select modules from " + ROLE_TABLE + " where role = ?", new ScalarHandler<String>(), role);
        if(s != null) {
            modules = Arrays.asList(s.split(", "));
        }

        return new ForwardResolution(JSP);
    }

    public Resolution delete() throws Exception {
        // TODO: https://stackoverflow.com/questions/24895177/how-to-access-sessions-in-tomcat-and-terminate-one-of-them

        if(ROLE_ADMIN.equals(role)) {
            getContext().getValidationErrors().addGlobalError(new SimpleError("Admin groep kan niet verwijderd worden"));
            return list();
        }

        int count = qr().update("delete from " + USER_ROLE_TABLE + " where role = ?", role);
        log.info("Removing role " + role + ", deleted " + count + " user roles ");
        count = qr().update("delete from " + ROLE_TABLE + " where role = ?", role);

        if(count != 0) {
            getContext().getMessages().add(new SimpleMessage("Groep is verwijderd"));
        } else {
            getContext().getMessages().add(new SimpleMessage("Geen groep gevonden om te verwijderen!"));
        }
        return new RedirectResolution(this.getClass()).flash(this);
    }

    public Resolution save() throws Exception {

        if(ROLE_ADMIN.equals(role)) {
            getContext().getValidationErrors().addGlobalError(new SimpleError("Admin groep kan niet aangepast worden"));
            return list();
        }

        String s = String.join(", ", modules.toArray(new String[]{}));

        int update = qr().update("update " + ROLE_TABLE + " set modules = ? where role = ?", s, role);
        if(update == 0) {
            qr().update("insert into " + ROLE_TABLE + " (role, modules) values(?, ?)", role, s);
        }

        getContext().getMessages().add(new SimpleMessage("Groep is opgeslagen"));
        return new RedirectResolution(this.getClass()).flash(this);
    }

    @DontValidate
    public Resolution cancel() {
        return new RedirectResolution(this.getClass()).flash(this);
    }
}
