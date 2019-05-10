package nl.opengeogroep.safetymaps.server.security;

import java.io.IOException;
import javax.servlet.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import java.security.Principal;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import static nl.opengeogroep.safetymaps.server.db.DB.USERNAME_LDAP;
import static nl.opengeogroep.safetymaps.server.db.DB.qr;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
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

    private static final String SESSION_PRINCIPAL = PersistentAuthenticationFilter.class.getName() + ".PRINCIPAL";
    
    private static final int COOKIE_EXPIRY = 60 * 60 * 24 * 365 * 10;

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
                chain.doFilter(request, response);
                return;
            }

            // Create new persistent session
            Calendar c = Calendar.getInstance();
            c.add(Calendar.SECOND, COOKIE_EXPIRY);
            String id = PersistentSessionManager.createPersistentSession(request, c.getTime());
            Cookie cookie = new Cookie(COOKIE_NAME, id);
            cookie.setPath(request.getServletContext().getContextPath()); // Set path so we can clear cookie on logout
            cookie.setHttpOnly(true);
            cookie.setSecure(request.getScheme().equals("https"));
            cookie.setMaxAge(COOKIE_EXPIRY);
            log.info("Request externally authenticated for user " + principal.getName() + ", setting persistent login cookie " + cookie);
            response.addCookie(cookie);
            chain.doFilter(request, response);
            return;
        }

        PersistentAuthenticatedPrincipal persistentPrincipal = (PersistentAuthenticatedPrincipal)request.getSession().getAttribute(SESSION_PRINCIPAL);

        if(persistentPrincipal != null) {
            log.trace("Request authenticated by cookie for user " + persistentPrincipal.getName() + ", using principal from session: " + request.getRequestURI());
        } else if(authCookie == null) {
            log.info("User not authenticated externally and no persistent session cookie, redirect tologin page");
            response.sendRedirect(request.getServletContext().getContextPath() + this.loginUrl);
            return;
        } else {

            if(persistentSession != null) {
                // persistentSession was found and not expired
                String obfuscatedSession = (String)persistentSession.get("id");
                obfuscatedSession = StringUtils.repeat("x", obfuscatedSession.length() / 2) + obfuscatedSession.substring(obfuscatedSession.length() / 2);
                log.info("Using authentication from cookie session for user " + persistentSession.get("username") + " from session id " + obfuscatedSession + ", saving principal in session: " + request.getRequestURI());

                try {
                    // Changes in authorizations
                    List<String> roles;
                    if("LDAP".equals(persistentSession.get("login_source"))) {
                        roles = qr().query("select role from safetymaps.user_roles where username = ?", new ColumnListHandler<String>(), USERNAME_LDAP);
                        // XXX original LDAP roles lost. Maybe
                        // - Save to database (needs reflection to get list (see authinfo.jsp), role changes in LDAP never propagated)
                        // - Recheck using JNDI.. (double LDAP config, extra code)
                        // For now only grant roles special user has
                        roles.add("LDAPUser");
                        log.warn("Returning LDAP user " + persistentSession.get("username") + " using persistent session cookie, only granting LDAPUser role and roles granted to special user '" + USERNAME_LDAP + "', roles: " + roles.toString());
                    } else {
                        roles = qr().query("select role from safetymaps.user_roles where username = ?", new ColumnListHandler<String>(), persistentSession.get("username"));
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

                    persistentPrincipal = new PersistentAuthenticatedPrincipal((String)persistentSession.get("username"), new HashSet(roles));
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

        final PersistentAuthenticatedPrincipal thePrincipal = persistentPrincipal;
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public String getRemoteUser() {
                return thePrincipal.getName();
            }

            @Override
            public Principal getUserPrincipal() {
                return thePrincipal;
            }

            @Override
            public boolean isUserInRole(String role) {
                return thePrincipal.isUserInRole(role);
            }
        }, response);
    }

    private class PersistentAuthenticatedPrincipal implements Principal {
        private final String name;
        private final Set<String> roles;

        public PersistentAuthenticatedPrincipal(String name, Set<String> roles) {
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
