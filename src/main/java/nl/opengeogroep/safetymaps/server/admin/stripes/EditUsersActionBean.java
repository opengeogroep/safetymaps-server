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

import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.Before;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.DontValidate;
import net.sourceforge.stripes.action.ForwardResolution;
import net.sourceforge.stripes.action.RedirectResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.SimpleMessage;
import net.sourceforge.stripes.action.StrictBinding;
import net.sourceforge.stripes.action.UrlBinding;
import net.sourceforge.stripes.validation.SimpleError;
import net.sourceforge.stripes.validation.Validate;
import net.sourceforge.stripes.validation.ValidationErrorHandler;
import net.sourceforge.stripes.validation.ValidationErrors;
import nl.opengeogroep.safetymaps.server.security.PersistentSessionManager;
import nl.opengeogroep.safetymaps.server.security.UpdatableLoginSessionFilter;
import org.apache.catalina.realm.SecretKeyCredentialHandler;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;

import javax.naming.NamingException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_TABLE;
import static nl.opengeogroep.safetymaps.server.db.DB.SESSION_TABLE;
import static nl.opengeogroep.safetymaps.server.db.DB.USERNAME_LDAP;
import static nl.opengeogroep.safetymaps.server.db.DB.USER_ADMIN;
import static nl.opengeogroep.safetymaps.server.db.DB.USER_ROLE_TABLE;
import static nl.opengeogroep.safetymaps.server.db.DB.USER_TABLE;
import static nl.opengeogroep.safetymaps.server.db.DB.qr;

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

    private List<Map<String, Object>> allRoles;

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

    @Validate
    private String voertuignummer;

    // <editor-fold defaultstate="collapsed" desc="getters and setters">
    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public List<Map<String, Object>> getAllRoles() {
        return allRoles;
    }

    public void setAllRoles(List<Map<String, Object>> allRoles) {
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

    public String getVoertuignummer() {
        return voertuignummer;
    }

    public void setVoertuignummer(String voertuignummer) {
        this.voertuignummer = voertuignummer;
    }
    // </editor-fold>

    @Before
    private void loadInfo() throws NamingException, SQLException {
        allRoles = qr().query("select role, coalesce(description, role) as description from " + ROLE_TABLE + " where protected = false or (protected = true and (left(role, 6) = 'smvng_' or role = 'admin')) order by protected desc, role", new MapListHandler());

        allUsers = qr().query("select 'userDatabase' as login_source, username, length(password) > 40 as secure_password, (select count(*) from " + SESSION_TABLE + " ps where ps.username = u.username) as session_count, (select max(created_at) from " + SESSION_TABLE + " ps where ps.username = u.username) as last_login\n" +
                "from " + USER_TABLE + " u\n" +
                "union\n" +
                "select 'LDAP' as login_source, username, null as secure_password, (select count(*) from " + SESSION_TABLE + " ps where ps.username = ps1.username) as session_count, (select max(created_at) from " + SESSION_TABLE + " ps where ps.username = ps1.username) as last_login\n" +
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

            if(data.get("details") != null) {
                JSONObject details = new JSONObject(data.get("details").toString());
                voertuignummer = details.optString("voertuignummer", null);
            }
        }

        return new ForwardResolution(JSP);
    }

    public Resolution delete() throws Exception {
        if(USER_ADMIN.equals(username) || USERNAME_LDAP.equals(username)) {
            getContext().getValidationErrors().addGlobalError(new SimpleError("Speciale gebruiker kan niet verwijderd worden"));
            return list();
        }
        UpdatableLoginSessionFilter.invalidateUserSessions(username);

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

    public Resolution deleteSessions() throws Exception {
        UpdatableLoginSessionFilter.invalidateUserSessions(username);
        getContext().getMessages().add(new SimpleMessage("Inlogsessies zijn verwijderd"));
        return new RedirectResolution(this.getClass()).flash(this);
    }

    public Resolution remoteLogin() throws Exception {
        SecretKeyCredentialHandler credentialHandler = new SecretKeyCredentialHandler();
        credentialHandler.setAlgorithm("PBKDF2WithHmacSHA512");
        credentialHandler.setIterations(100000);
        credentialHandler.setKeyLength(256);
        credentialHandler.setSaltLength(16);
        String tempPassword = RandomStringUtils.random(12, true, true);
        String tempHashedPassword = credentialHandler.mutate(tempPassword);
        String userHashedPassword = qr().query("select password from " + USER_TABLE + " where username = ?", new ScalarHandler<String>(), username);

        HttpServletRequest request = getContext().getRequest();
        HttpServletResponse response = getContext().getResponse();
        
        Cookie authCookie = null;
        if(request.getCookies() != null) {
            for(Cookie cookie: request.getCookies()) {
                if("sm-plogin".equals(cookie.getName())) {
                    authCookie = cookie;
                }
            }
        }

        if(authCookie != null) {
            String sessionId = authCookie.getValue();
            log.info("Clearing persistent login cookie for persistent session ID " + sessionId + " on logout");
            Cookie cookie = new Cookie("sm-plogin", sessionId);
            cookie.setMaxAge(0);
            response.addCookie(cookie);
            PersistentSessionManager.deleteSession(sessionId);
        }
        
        qr().update("update " + USER_TABLE + " set password = ? where username = ?", tempHashedPassword, username);

        request.logout();
        request.getSession().invalidate();
        request.getSession();
        request.login(username, tempPassword);

        qr().update("update " + USER_TABLE + " set password = ? where username = ?", userHashedPassword, username);

        return new RedirectResolution("/smvng/test");
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
            // We need to construct this Tomcat class ourselves, because we use a NestedCredentialHandler, see:
            // https://stackoverflow.com/questions/64733766/how-to-get-tomcat-credentialhandler-inside-java-when-nested-in-lockoutrealm
            SecretKeyCredentialHandler credentialHandler = new SecretKeyCredentialHandler();
            credentialHandler.setAlgorithm("PBKDF2WithHmacSHA512");
            credentialHandler.setIterations(100000);
            credentialHandler.setKeyLength(256);
            credentialHandler.setSaltLength(16);
            hashedPassword = credentialHandler.mutate(password);

            if(!USER_ADMIN.equals(username)) {
                UpdatableLoginSessionFilter.invalidateUserSessions(username);
            }
        }

        String detailsString = null;
        if(voertuignummer != null) {
            // Assume only we save details, if other places use details, update
            // result from DB.getUserDetails()
            JSONObject details = new JSONObject();
            details.put("voertuignummer", voertuignummer);
            detailsString = details.toString();
        }

        int update = qr().update("update " + USER_TABLE + " set password = ?, session_expiry_number = ?, session_expiry_timeunit = ?, details = ?::json where username = ?", hashedPassword, expiry, expiryTimeUnit, detailsString, username);
        if(update == 0) {
            qr().update("insert into " + USER_TABLE + " (username, password, session_expiry_number, session_expiry_timeunit) values(?, ?, ?, ?)", username, hashedPassword, expiry, expiryTimeUnit);
        }
        qr().update("delete from " + USER_ROLE_TABLE + " where username = ?", username);
        if(roles != null) {
            for(String r: roles) {
                qr().update("insert into " + USER_ROLE_TABLE + " (username, role) values (?, ?)", username, r);
            }
        }
        qr().update("insert into " + USER_ROLE_TABLE + " (username, role) values (?, ?)", username, "viewer");
        qr().update("insert into " + USER_ROLE_TABLE + " (username, role) values (?, ?)", username, "safetyconnect_webservice");
        
        UpdatableLoginSessionFilter.updateUserSessionRoles(username);

        getContext().getMessages().add(new SimpleMessage("Gebruiker is opgeslagen"));
        return new RedirectResolution(this.getClass()).flash(this);
    }

    @DontValidate
    public Resolution cancel() {
        return new RedirectResolution(this.getClass()).flash(this);
    }
}
