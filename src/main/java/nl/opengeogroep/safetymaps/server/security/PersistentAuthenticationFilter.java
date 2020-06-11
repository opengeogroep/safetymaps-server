package nl.opengeogroep.safetymaps.server.security;

import java.io.IOException;
import javax.servlet.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import java.security.Principal;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.naming.NamingException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import static nl.opengeogroep.safetymaps.server.db.DB.USER_TABLE;
import static nl.opengeogroep.safetymaps.server.db.DB.qr;
import static nl.opengeogroep.safetymaps.utils.SameSiteCookieUtil.addCookieWithSameSite;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 *
 * @author matthijsln
 */
public class PersistentAuthenticationFilter implements Filter {

    private static final Log log = LogFactory.getLog(PersistentAuthenticationFilter.class);

    private FilterConfig filterConfig = null;

    /**
     * Prefix for context global parameters which can be set to override filter
     * init params for easier deployments without overwriting web.xml.
     */
    private static final String CONTEXT_PARAM_PREFIX = "persistentAuth";

    private static final String COOKIE_NAME = "sm-plogin";

    private static final String DEFAULT_LOGIN_SOURCE = "SafetyMaps";

    private static final String SESSION_PRINCIPAL = PersistentAuthenticationFilter.class.getName() + ".PRINCIPAL";
    
    private static final int EXPIRY_DEFAULT_UNIT = Calendar.YEAR;
    private static final int EXPIRY_DEFAULT = 10;

    private static final String PARAM_ENABLED = "enabled";
    private static final String PARAM_PERSISTENT_LOGIN_PATH_PREFIX = "persistentLoginPathPrefix";
    private static final String PARAM_LOGOUT_URL = "logoutUrl";
    private static final String PARAM_ROLES_AS_DB_USERNAMES = "rolesAsDbUsernames";

    private String persistentLoginPrefix;
    private String[] rolesAsDbUsernames;
    private boolean enabled;
    private String logoutUrl;

    /**
     * Get a filter init-parameter which can be overridden by a context parameter
     * when prefixed with "persistentAuth".
     */
    private String getInitParameter(String paramName) {
        String contextParamName = CONTEXT_PARAM_PREFIX + StringUtils.capitalize(paramName);
        String value = filterConfig.getServletContext().getInitParameter(contextParamName);
        if(value == null) {
            value = filterConfig.getInitParameter(paramName);
            log.debug("Using filter init parameter " + paramName + ": " + value);
        } else {
            log.debug("Using context parameter " + contextParamName + ": " + value);
        }
        return value;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        this.filterConfig = filterConfig;

        this.enabled = "true".equals(ObjectUtils.firstNonNull(getInitParameter(PARAM_ENABLED), "true"));
        this.persistentLoginPrefix = ObjectUtils.firstNonNull(getInitParameter(PARAM_PERSISTENT_LOGIN_PATH_PREFIX), "/");
        this.rolesAsDbUsernames =  ObjectUtils.firstNonNull(getInitParameter(PARAM_ROLES_AS_DB_USERNAMES), "").split(",");
        this.logoutUrl = ObjectUtils.firstNonNull(getInitParameter(PARAM_LOGOUT_URL), "/logout.jsp");

        UpdatableLoginSessionFilter.monitorSessionInvalidation(new SessionInvalidateMonitor() {
            @Override
            public void invalidateUserSessions(String username) {
                try {
                    PersistentSessionManager.deleteUserSessions(username);
                } catch(Exception e) {
                    log.error("Error removing persistent sessions for user " + username, e);
                }
            }
        });

        log.info("Initialized - " + toString());
    }

    @Override
    public void destroy() {
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    private void chainWithPrincipal(final HttpServletRequest request, HttpServletResponse response, FilterChain chain, final AuthenticatedPrincipal principal) throws IOException, ServletException {
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public String getRemoteUser() {
                return principal.getName();
            }

            @Override
            public Principal getUserPrincipal() {
                return principal;
            }

            @Override
            public boolean isUserInRole(String role) {
                return request.isUserInRole(role) || principal.isUserInRole(role);
            }
        }, response);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest)servletRequest;
        HttpServletResponse response = (HttpServletResponse)servletResponse;
        HttpSession session = request.getSession();

        if(!enabled) {
            chain.doFilter(request, response);
            return;
        }

        Cookie authCookie = null;
        Map<String,Object> persistentSession = null;
        if(request.getCookies() != null) {
            for(Cookie cookie: request.getCookies()) {
                if(COOKIE_NAME.equals(cookie.getName())) {
                    authCookie = cookie;
                }
            }
        }

        if(request.getRequestURI().startsWith(request.getContextPath() + logoutUrl)) {
            if(authCookie != null) {
                String sessionId = authCookie.getValue();
                log.info("Clearing persistent login cookie for persistent session ID " + sessionId + " on logout");
                Cookie cookie = new Cookie(COOKIE_NAME, sessionId);
                cookie.setMaxAge(0);
                response.addCookie(cookie);
                PersistentSessionManager.deleteSession(sessionId);
            }
            session.invalidate();
            chain.doFilter(request, response);
            return;
        }

        if(!request.getRequestURI().startsWith(request.getContextPath() + persistentLoginPrefix)) {
            // No authentication required
            chain.doFilter(request, response);
            return;
        }

        if(authCookie != null) {
            String sessionId = authCookie.getValue();
            persistentSession = PersistentSessionManager.getValidPersistentSession(request, sessionId);
        }

        Principal principal = request.getUserPrincipal();
        if(principal != null) {

            // If valid persistent session already set, do nothing
            if(persistentSession != null) {
                log.trace(request.getRequestURI() + ": Request external authenticated for user " + principal.getName() + ", persistent session verified, path: " + request.getRequestURI());

                chain.doFilter(servletRequest, servletResponse);
                return;
            } else {

                // Create new persistent session, if the user exists in the database.

                // If the request.getRemoteUser() name does not exist in the database
                // (authenticated externally), no persistent session is created, unless
                // the request is in a role which we will use as a db username instead
                // to get the persistent session settings.

                String dbUsername = request.getRemoteUser();

                for(String role: rolesAsDbUsernames) {
                    if(request.isUserInRole(role)) {
                        log.trace(request.getRequestURI() + ": Request external authenticated and in role " + role + " which will be used as username to get persistent session settings");
                        dbUsername = role;
                        break;
                    }
                }

                Map<String,Object> data;
                try {
                    data = qr().query("select * from " + USER_TABLE + " where username = ?", new MapHandler(), dbUsername);
                } catch(SQLException | NamingException e) {
                    throw new IOException(e);
                }
                // If user is deleted or from external source not in a rule that was configured
                // to use as a username to get persistent login settings, do not create a persistent
                // session
                if(data == null) {
                    if(log.isTraceEnabled()) {
                        log.trace(request.getRequestURI() + ": Request authenticated but not creating persistent session because could not find username " + dbUsername + " in db");
                    }
                    chain.doFilter(request, response);
                    return;
                }
                Integer expiry = data.containsKey("session_expiry_number") ? (Integer)data.get("session_expiry_number") : null;
                String expiryTimeUnit = (String)data.get("session_expiry_timeunit");

                Calendar c = Calendar.getInstance();
                if(expiry != null) {
                    if(expiryTimeUnit.equals("days")) {
                        c.add(Calendar.DAY_OF_YEAR, expiry);
                    } else if(expiryTimeUnit.equals("weeks")) {
                        c.add(Calendar.WEEK_OF_YEAR, expiry);
                    } else if(expiryTimeUnit.equals("months")) {
                        c.add(Calendar.MONTH, expiry);
                    } else if(expiryTimeUnit.equals("years")) {
                        c.add(Calendar.YEAR, expiry);
                    } else {
                        c.add(EXPIRY_DEFAULT_UNIT, EXPIRY_DEFAULT);
                    }
                } else {
                    c.add(EXPIRY_DEFAULT_UNIT, EXPIRY_DEFAULT);
                }
                String loginSource = DEFAULT_LOGIN_SOURCE;
                if(!dbUsername.equals(request.getRemoteUser())) {
                    loginSource = dbUsername;
                }
                String id = PersistentSessionManager.createPersistentSession(request, loginSource, c.getTime());
                Cookie cookie = new Cookie(COOKIE_NAME, id);
                String path = request.getServletContext().getContextPath();
                if("".equals(path)) {
                    // for ROOT webapp the context path is "", make sure cookie path is
                    // set to "/", otherwise cookie will not be set on /logout.jsp,
                    // making logging out impossible
                    path = "/";
                }
                cookie.setPath(path); // Set path so we can clear cookie on logout
                cookie.setHttpOnly(true);
                cookie.setSecure(request.getScheme().equals("https"));
                cookie.setMaxAge((int)((c.getTimeInMillis() - System.currentTimeMillis()) / 1000));
                log.info(request.getRequestURI() + ": Request externally authenticated for user " + principal.getName() + ", setting persistent login cookie " + obfuscateSessionId(id));
                addCookieWithSameSite(response, cookie, "None");
            }
        }

        AuthenticatedPrincipal persistentPrincipal = (AuthenticatedPrincipal)session.getAttribute(SESSION_PRINCIPAL);

        if(persistentPrincipal != null) {
            log.trace(request.getRequestURI() + ": Request authenticated by cookie for user " + persistentPrincipal.getName() + ", using principal from session: " + request.getRequestURI());
        } else if(authCookie == null) {
            log.trace(request.getRequestURI() + ": User not authenticated externally and no persistent session cookie, pass through (auth must be checked after this filter)");
            chain.doFilter(request, response);
            return;
        } else {

            if(persistentSession != null) {
                // persistentSession was found and not expired
                String obfuscatedSession = obfuscateSessionId((String)persistentSession.get("id"));
                log.info(request.getRequestURI() + ": Using authentication from cookie session for user " + persistentSession.get("username") + " from session id " + obfuscatedSession + ", saving principal in session: " + request.getRequestURI());

                // Make sure principal has role from login source if different from DEFAULT_LOGIN_SOURCE,
                // so UpdatableLoginSessionFilter can get roles using that role name as username

                Set<String> roles = new HashSet();
                if(!DEFAULT_LOGIN_SOURCE.equals(persistentSession.get("login_source"))) {
                    roles.add((String)persistentSession.get("login_source"));
                }

                persistentPrincipal = new AuthenticatedPrincipal((String)persistentSession.get("username"), roles);
                request.getSession().setAttribute(SESSION_PRINCIPAL, persistentPrincipal);
            } else {
                log.info(request.getRequestURI() + "User not authenticated externally, persistent cookie is not valid, pass through (auth must be checked after this filter)");
                chain.doFilter(request, response);
                return;
            }
        }

        chainWithPrincipal(request, response, chain, persistentPrincipal);
    }

    private static String obfuscateSessionId(String id) {
        return StringUtils.repeat("x", id.length() / 2) + id.substring(id.length() / 2);
    }

    private class AuthenticatedPrincipal implements Principal {
        private final String name;
        private final Set<String> roles;

        public AuthenticatedPrincipal(String name, Set<String> roles) {
            this.name = name;
            this.roles = roles;
        }

        @Override
        public String getName() {
            return name;
        }

        public boolean isUserInRole(String r) {
            return roles.contains(r);
        }

        public Set<String> getRoles() {
            return roles;
        }

        public void setRoles(Set<String> roles) {
            throw new UnsupportedOperationException();
        }
    }
}
