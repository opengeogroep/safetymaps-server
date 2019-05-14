/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.server.security;

import java.io.IOException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import static nl.opengeogroep.safetymaps.server.db.DB.USER_TABLE;
import static nl.opengeogroep.safetymaps.server.db.DB.qr;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author matthijsln
 */
public class PersistentSessionManager {
    private static Log log = LogFactory.getLog(PersistentSessionManager.class);

    // commonRole attribut from META-INF/context.xml <Realm className="org.apache.catalina.realm.JNDIRealm"/> element
    public static final String LDAP_GROUP = "LDAPUser";

    private static final String SESSION_TABLE = "safetymaps.persistent_session ";

    private static final int SESSION_ID_LENGTH = 16;

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final String LOWER = UPPER.toLowerCase(Locale.ROOT);

    private static final String DIGITS = "0123456789";

    private static final char[] ALPHANUM = (UPPER + LOWER + DIGITS).toCharArray();

    private static final Random RANDOM = new SecureRandom();

    private static String getNewSessionID() {
        char[] buf = new char[SESSION_ID_LENGTH];
        for (int i = 0; i < buf.length; ++i) {
            buf[i] = ALPHANUM[RANDOM.nextInt(ALPHANUM.length)];
        }
        return new String(buf);
    }

    public static String createPersistentSession(HttpServletRequest request, java.util.Date expiresAt) throws IOException {

        String id = getNewSessionID();
        String username = request.getRemoteUser();
        Objects.requireNonNull(username);
        String remoteIpLogin = request.getRemoteAddr();

        String loginSource = "userDatabase";
        if(request.isUserInRole(LDAP_GROUP)) {
            loginSource = "LDAP";
        }

        try {
            qr().update("insert into " + SESSION_TABLE + "(id, username, created_at, expires_at, remote_ip_login, remote_ip_last, login_source) values (?, ?, ?, ?, ?, ?, ?)",
                    id,
                    username,
                    new java.sql.Timestamp(new java.util.Date().getTime()),
                    new java.sql.Timestamp(expiresAt.getTime()),
                    remoteIpLogin,
                    remoteIpLogin,
                    loginSource);
        } catch(SQLException | NamingException e) {
            throw new IOException("Database error", e);
        }

        return id;
    }

    public static void removeInvalidSessions() throws SQLException, NamingException {
        // TODO: for performance maybe only every x seconds

        log.debug("Checking for invalid sessions to remove");
        List<Map<String,Object>> expiredSessions = qr().query("select * from " + SESSION_TABLE + " where expires_at < now()", new MapListHandler());
        if(!expiredSessions.isEmpty()) {
            log.info("Removing " + expiredSessions.size() + " expired sessions");
            for(Map<String,Object> session: expiredSessions) {
                log.info(String.format("Removing session id %s, expired at %tc for user %s", session.get("id"), new Date(((java.sql.Timestamp)session.get("expires_at")).getTime()), session.get("username")));
            }
            qr().update("delete from " + SESSION_TABLE + " where expires_at < now()");
        }

        // Double check; do not rely on row being deleted when removing a user via user interface, maybe direct db delete by dba!
        List<Map<String,Object>> removedUserSessions = qr().query("select * from " + SESSION_TABLE + " ps where login_source = 'userDatabase' and not exists (select 1 from " + USER_TABLE + " u where u.username = ps.username)", new MapListHandler());
        if(!removedUserSessions.isEmpty()) {
            log.info("Removing " + removedUserSessions.size() + " sessions for deleted users");
            for(Map<String,Object> session: expiredSessions) {
                log.info(String.format("Removing session id %s for non-existant user %s", session.get("id"), session.get("username")));
            }
            qr().update("delete from " + SESSION_TABLE + " ps where login_source = 'userDatabase' and not exists (select 1 from " + USER_TABLE + " u where u.username = ps.username)");
        }
    }

    public static Map<String,Object> getValidPersistentSession(HttpServletRequest request, String id) throws IOException {
        log.debug("Check for valid persistent session for id " + id);
        try {
            removeInvalidSessions();

            Map result = qr().query("select * from " + SESSION_TABLE + " where id = ?", new MapHandler(), id);
            log.debug("Result: " + result);
            if(result != null && !request.getRemoteAddr().equals(result.get("remote_ip_last"))) {
                log.info(String.format("Changed remote address for session %s from %s to %s", result.get("id"), result.get("remote_ip_last"), request.getRemoteAddr()));
                qr().update("update " + SESSION_TABLE + " set remote_ip_last = ? where id = ?", request.getRemoteAddr(), result.get("id"));
            }
            return result;
        } catch(SQLException | NamingException e) {
            throw new IOException("Database error", e);
        }
    }

    public static void deleteSession(String id) throws IOException {
        log.debug("Deleting session for id " + id);
        try {
            qr().update("delete from " + SESSION_TABLE + " where id = ?", id);
        } catch(SQLException | NamingException e) {
            throw new IOException("Database error", e);
        }
    }
}
