package nl.opengeogroep.safetymaps.server.security;

import java.io.IOException;
import javax.servlet.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import java.security.Principal;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.naming.NamingException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import nl.opengeogroep.safetymaps.server.db.DB;
import static nl.opengeogroep.safetymaps.server.db.DB.USERNAME_LDAP;
import static nl.opengeogroep.safetymaps.server.db.DB.USER_TABLE;
import static nl.opengeogroep.safetymaps.server.db.DB.qr;
import static nl.opengeogroep.safetymaps.server.security.PersistentSessionManager.LDAP_GROUP;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
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

    private static final String SESSION_USERNAME = PersistentAuthenticationFilter.class.getName() + ".USERNAME";
    private static final String SESSION_PRINCIPAL = PersistentAuthenticationFilter.class.getName() + ".PRINCIPAL";
    private static final String SESSION_ADDITIONAL_ROLES = PersistentAuthenticationFilter.class.getName() + ".ADDITIONAL_ROLES";
    
    private static final int EXPIRY_DEFAULT_UNIT = Calendar.YEAR;
    private static final int EXPIRY_DEFAULT = 10;

    private static final String PARAM_ENABLED = "enabled";
    private static final String PARAM_PERSISTENT_LOGIN_PATH_PREFIX = "persistentLoginPathPrefix";
    private static final String PARAM_ALLOWED_ROLES = "allowedRoles";
    private static final String PARAM_LOGIN_URL = "loginUrl";
    private static final String PARAM_LOGOUT_URL = "logoutUrl";

    private String persistentLoginPrefix;
    private boolean enabled;
    private String loginUrl;
    private String logoutUrl;
    private List<String> allowedRoles;

    private static final ConcurrentMap<String,HttpSession> CONTAINER_SESSIONS = new ConcurrentHashMap();

    /**
     * Get a filter init-parameter which can be overriden by a context parameter
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

        this.loginUrl = ObjectUtils.firstNonNull(getInitParameter(PARAM_LOGIN_URL), "/viewer/api/login");
        this.logoutUrl = ObjectUtils.firstNonNull(getInitParameter(PARAM_LOGOUT_URL), "/logout.jsp");
        this.allowedRoles =  Arrays.asList(ObjectUtils.firstNonNull(getInitParameter(PARAM_ALLOWED_ROLES), "").split(","));
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

    private void addCookieWithSameSite(HttpServletRequest request, HttpServletResponse response, Cookie cookie, String sameSite) {

        OffsetDateTime expires = OffsetDateTime.now(ZoneOffset.UTC).plus(Duration.ofSeconds(cookie.getMaxAge()));
        String cookieExpires = DateTimeFormatter.RFC_1123_DATE_TIME.format(expires);

        String value = String.format("%s=%s; Max-Age=%d; Expires=%s; Path=%s",
                cookie.getName(),
                cookie.getValue(),
                cookie.getMaxAge(),
                cookieExpires,
                cookie.getPath());

        List attributes = new ArrayList();
        if(cookie.getDomain() != null) {
            attributes.add("Domain=" + cookie.getDomain());
        }
        if(cookie.isHttpOnly()) {
            attributes.add("HttpOnly");
        }
        if(cookie.getSecure()) {
            attributes.add("Secure");
        }
        if(sameSite != null) {
            attributes.add("SameSite=" + sameSite);
        }
        if(!attributes.isEmpty()) {
            value += "; " + String.join("; ", attributes);
        }

        response.addHeader("Set-Cookie", value);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest)servletRequest;
        HttpServletResponse response = (HttpServletResponse)servletResponse;
        HttpSession session = request.getSession();

        CONTAINER_SESSIONS.putIfAbsent(session.getId(), session);

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
            session.setAttribute(SESSION_USERNAME, principal.getName());

            boolean allowed = false;
            for(String role: allowedRoles) {
                if(request.isUserInRole(role)) {
                    allowed = true;
                    break;
                }
            }
            if(!allowed) {
                log.warn("User " + request.getRemoteUser() + " has no access to voertuigviewer (authenticated by user database)");
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Toegang tot voertuigviewer geweigerd");
                return;
            }

            // If valid persistent session already set, do nothing
            if(persistentSession != null) {
                log.trace("Request external authenticated for user " + principal.getName() + ", persistent session verified, path: " + request.getRequestURI());

                // If LDAP user, apply additional roles from DB user set in session
                // in other branch of this if statement
                AuthenticatedPrincipal ldapPrincipal = (AuthenticatedPrincipal)session.getAttribute(SESSION_PRINCIPAL);
                if(ldapPrincipal != null) {
                    chainWithPrincipal(request, response, chain, ldapPrincipal);
                } else {
                    chain.doFilter(servletRequest, servletResponse);
                }
                return;

            } else {

                // Create new persistent session
                String dbUsername = request.getRemoteUser();
                if(request.isUserInRole(LDAP_GROUP)) {
                    dbUsername = USERNAME_LDAP;
                }

                Map<String,Object> data;
                try {
                    data = qr().query("select * from " + USER_TABLE + " where username = ?", new MapHandler(), dbUsername);
                } catch(SQLException | NamingException e) {
                    throw new IOException(e);
                }
                // If user is deleted...
                if(data == null) {
                    session.invalidate();
                    chain.doFilter(request, response);
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
                String id = PersistentSessionManager.createPersistentSession(request, c.getTime());
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
                log.info("Request externally authenticated for user " + principal.getName() + ", setting persistent login cookie " + obfuscateSessionId(id));
                addCookieWithSameSite(request, response, cookie, "None");

                // Apply additional roles if LDAP user
                if(request.isUserInRole(LDAP_GROUP)) {
                    try {
                        List<String> roles = qr().query("select role from " + DB.USER_ROLE_TABLE + " where username = ?", new ColumnListHandler<String>(), dbUsername);
                        AuthenticatedPrincipal ldapPrincipal = new AuthenticatedPrincipal(principal.getName(), new HashSet<>(roles));
                        log.info("Apply additional roles for this LDAP user in this session: " + roles);
                        session.setAttribute(SESSION_PRINCIPAL, ldapPrincipal);
                        chainWithPrincipal(request, response, chain, ldapPrincipal);
                        return;
                    } catch(SQLException | NamingException e) {
                        throw new IOException(e);
                    }
                }
            }
        }

        AuthenticatedPrincipal persistentPrincipal = (AuthenticatedPrincipal)session.getAttribute(SESSION_PRINCIPAL);

        if(persistentPrincipal != null) {
            log.trace("Request authenticated by cookie for user " + persistentPrincipal.getName() + ", using principal from session: " + request.getRequestURI());
        } else if(authCookie == null) {
            log.info("User not authenticated externally and no persistent session cookie, redirect tologin page");
            response.sendRedirect(request.getServletContext().getContextPath() + this.loginUrl);
            return;
        } else {

            if(persistentSession != null) {
                // persistentSession was found and not expired
                String obfuscatedSession = obfuscateSessionId((String)persistentSession.get("id"));
                log.info("Using authentication from cookie session for user " + persistentSession.get("username") + " from session id " + obfuscatedSession + ", saving principal in session: " + request.getRequestURI());

                try {
                    // Changes in authorizations
                    List<String> roles;
                    if("LDAP".equals(persistentSession.get("login_source"))) {
                        roles = qr().query("select role from " + DB.USER_ROLE_TABLE + " where username = ?", new ColumnListHandler<String>(), USERNAME_LDAP);
                        // XXX original LDAP roles lost. Maybe
                        // - Save to database (needs reflection to get list (see authinfo.jsp), role changes in LDAP never propagated)
                        // - Recheck using JNDI.. (double LDAP config, extra code)
                        // For now only grant roles special user has
                        roles.add("LDAPUser");
                        log.warn("Returning LDAP user " + persistentSession.get("username") + " using persistent session cookie, only granting LDAPUser role and roles granted to special user '" + USERNAME_LDAP + "', roles: " + roles.toString());
                    } else {
                        roles = qr().query("select role from " + DB.USER_ROLE_TABLE + " where username = ?", new ColumnListHandler<String>(), persistentSession.get("username"));
                    }

                    boolean allowed = false;
                    for(String role: allowedRoles) {
                        if(roles.contains(role)) {
                            allowed = true;
                            break;
                        }
                    }
                    if(!allowed) {
                        log.warn("User " + persistentSession.get("username") + " has no access to voertuigviewer (authenticated from persistent session cookie)");
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Toegang tot voertuigviewer geweigerd");
                        return;
                    }

                    persistentPrincipal = new AuthenticatedPrincipal((String)persistentSession.get("username"), new HashSet(roles));
                    request.getSession().setAttribute(SESSION_PRINCIPAL, persistentPrincipal);
                } catch(javax.naming.NamingException | java.sql.SQLException e) {
                    throw new IOException(e);
                }
            } else {
                log.info("User not authenticated externally, persistent cookie is not valid, redirect tologin page");
                response.sendRedirect(request.getServletContext().getContextPath() + this.loginUrl);
                return;
            }
        }

        session.setAttribute(SESSION_USERNAME, persistentPrincipal.getName());

        final AuthenticatedPrincipal thePrincipal = persistentPrincipal;
        chainWithPrincipal(request, response, chain, thePrincipal);

    }

    public static void invalidateUserSessions(String name) throws IOException {
        List<String> invalidSessionIds = new ArrayList<>();
        for(HttpSession session: CONTAINER_SESSIONS.values()) {
            try {
                String sessionUser = (String)session.getAttribute(SESSION_USERNAME);
                log.debug("Checking container session " + session.getId() + " to delete, session user = " + sessionUser);
                if(name.equals(sessionUser)) {
                    log.info("Invalidating container session for user " + name + ": " + session.getId());
                    session.invalidate();
                    invalidSessionIds.add(session.getId());
                }
            } catch(IllegalStateException e) {
                log.info("Session already invalid: " + session.getId());
                invalidSessionIds.add(session.getId());
            }
        }
        for(String id: invalidSessionIds) {
            CONTAINER_SESSIONS.remove(id);
        }
        PersistentSessionManager.deleteUserSessions(name);
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
