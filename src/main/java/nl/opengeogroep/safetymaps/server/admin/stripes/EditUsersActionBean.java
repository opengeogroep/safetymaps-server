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
import java.util.List;
import java.util.Map;
import javax.naming.NamingException;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.*;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_TABLE;
import static nl.opengeogroep.safetymaps.server.db.DB.SESSION_TABLE;
import static nl.opengeogroep.safetymaps.server.db.DB.USERNAME_LDAP;
import static nl.opengeogroep.safetymaps.server.db.DB.USER_ROLE_TABLE;
import static nl.opengeogroep.safetymaps.server.db.DB.USER_TABLE;
import static nl.opengeogroep.safetymaps.server.db.DB.qr;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author matthijsln
 */
@StrictBinding
@UrlBinding("/admin/action/users")
public class EditUsersActionBean implements ActionBean, ValidationErrorHandler {
    private static final Log log = LogFactory.getLog("admin.users");

    private static final String JSP = "/WEB-INF/jsp/admin/editUsers.jsp";

    private ActionBeanContext context;

    private List<String> allRoles;

    private List<Map<String,Object>> allUsers;

    @Validate(required = true, on={"save", "delete"})
    private String username;

    @Validate
    private String password;

    @Validate
    private Integer expiry;

    @Validate
    private String expiryTimeUnit;

    @Validate
    private List<String> roles;

    // <editor-fold defaultstate="collapsed" desc="getters and setters">
    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public List<String> getAllRoles() {
        return allRoles;
    }

    public void setAllRoles(List<String> allRoles) {
        this.allRoles = allRoles;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<Map<String, Object>> getAllUsers() {
        return allUsers;
    }

    public void setAllUsers(List<Map<String, Object>> allUsers) {
        this.allUsers = allUsers;
    }

    public Integer getExpiry() {
        return expiry;
    }

    public void setExpiry(Integer expiry) {
        this.expiry = expiry;
    }

    public String getExpiryTimeUnit() {
        return expiryTimeUnit;
    }

    public void setExpiryTimeUnit(String expiryTimeUnit) {
        this.expiryTimeUnit = expiryTimeUnit;
    }
    // </editor-fold>

    @Before
    private void loadInfo() throws NamingException, SQLException {
        allRoles = qr().query("select role from " + ROLE_TABLE + " order by protected desc, role", new ColumnListHandler<String>());

        allUsers = qr().query("select 'userDatabase' as login_source, username, (select count(*) from " + SESSION_TABLE + " ps where ps.username = u.username) as session_count, (select max(created_at) from " + SESSION_TABLE + " ps where ps.username = u.username) as last_login\n" +
                "from " + USER_TABLE + " u\n" +
                "union\n" +
                "select 'LDAP' as login_source, username, (select count(*) from " + SESSION_TABLE + " ps where ps.username = ps1.username) as session_count, (select max(created_at) from " + SESSION_TABLE + " ps where ps.username = ps1.username) as last_login\n" +
                "from " + SESSION_TABLE + " ps1\n" +
                "where login_source = 'LDAP'\n" +
                "order by username", new MapListHandler());
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
        if(username == null) {
            expiry = 10;
            expiryTimeUnit = "years";
        } else {
            roles = qr().query("select role from " + USER_ROLE_TABLE + " where username = ?", new ColumnListHandler<String>(), username);

            Map<String,Object> data = qr().query("select * from " + USER_TABLE + " where username = ?", new MapHandler(), username);
            expiry = data.containsKey("session_expiry_number") ? (Integer)data.get("session_expiry_number") : null;
            expiryTimeUnit = (String)data.get("session_expiry_timeunit");
        }

        return new ForwardResolution(JSP);
    }

    public Resolution delete() throws Exception {
        if("admin".equals(username) || USERNAME_LDAP.equals(username)) {
            getContext().getValidationErrors().addGlobalError(new SimpleError("Speciale gebruiker kan niet verwijderd worden"));
            return list();
        }
        // TODO: https://stackoverflow.com/questions/24895177/how-to-access-sessions-in-tomcat-and-terminate-one-of-them

        int count = qr().update("delete from " + SESSION_TABLE + " where username = ?", username);
        log.info("Removing user " + username + ", deleted " + count + " persistent sessions");
        qr().update("delete from " + USER_ROLE_TABLE + " where username = ?", username);
        count = qr().update("delete from " + USER_TABLE + " where username = ?", username);

        if(count != 0) {
            getContext().getMessages().add(new SimpleMessage("Gebruiker is verwijderd"));
        } else {
            getContext().getMessages().add(new SimpleMessage("Geen gebruiker gevonden om te verwijderen!"));
        }
        return new RedirectResolution(this.getClass()).flash(this);
    }

    public Resolution save() throws Exception {

        String hashedPassword;
        if(password == null) {
            hashedPassword = qr().query("select password from " + USER_TABLE + " where username = ?", new ScalarHandler<String>(), username);
            if(hashedPassword == null) {
                getContext().getValidationErrors().addGlobalError(new SimpleError("Gebruiker niet gevonden, kan wachtwoord niet wijzigen"));
                return new RedirectResolution(this.getClass()).flash(this);
            }
        } else {
            hashedPassword = DigestUtils.sha1Hex(password);
        }

        int update = qr().update("update " + USER_TABLE + " set password = ?, session_expiry_number = ?, session_expiry_timeunit = ? where username = ?", hashedPassword, expiry, expiryTimeUnit, username);
        if(update == 0) {
            qr().update("insert into " + USER_TABLE + " (username, password, session_expiry_number, session_expiry_timeunit) values(?, ?, ?, ?)", username, hashedPassword, expiry, expiryTimeUnit);
        }
        qr().update("delete from " + USER_ROLE_TABLE + " where username = ?", username);
        if(roles != null) {
            for(String r: roles) {
                qr().update("insert into " + USER_ROLE_TABLE + " (username, role) values (?, ?)", username, r);
            }
        }
        // TODO: update role list in sessions, access Sessions by shared Map username ?

        getContext().getMessages().add(new SimpleMessage("Gebruiker is opgeslagen"));
        return new RedirectResolution(this.getClass()).flash(this);
    }

    @DontValidate
    public Resolution cancel() {
        return new RedirectResolution(this.getClass()).flash(this);
    }
}
