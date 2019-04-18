package nl.opengeogroep.safetymaps.server;

import java.io.IOException;
import javax.servlet.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
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
    
    private static final int COOKIE_EXPIRY = 60 * 60 * 30;

    private static final String PARAM_ENABLED = "enabled";
    private static final String PARAM_PERSISTENT_LOGIN_PATH_PREFIX = "persistentLoginPathPrefix";
    private static final String PARAM_LOGIN_URL = "loginUrl";
    private static final String PARAM_LOGOUT_URL = "logoutUrl";

    private String persistentLoginPrefix;
    private boolean enabled;
    private String loginUrl;
    private String logoutUrl;

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
        this.logoutUrl = ObjectUtils.firstNonNull(getInitParameter(PARAM_LOGIN_URL), "/logout.jsp");
        /*this.headerPrefix = ObjectUtils.firstNonNull(getInitParameter(PARAM_HEADER_PREFIX), "[disabled]");
        this.enabled = !"[disabled]".equals(this.headerPrefix);
        this.userHeader = this.headerPrefix + ObjectUtils.firstNonNull(getInitParameter(PARAM_USER_HEADER), "_uid");
        this.authPath = ObjectUtils.firstNonNull(getInitParameter(PARAM_AUTH_PATH), "auth/saml");
        this.authInitPath = ObjectUtils.firstNonNull(getInitParameter(PARAM_AUTH_PATH), "auth/init");
        this.rolesHeader = this.headerPrefix + ObjectUtils.firstNonNull(getInitParameter(PARAM_ROLES_HEADER), "_roles");
        this.rolesSeparator = ObjectUtils.firstNonNull(getInitParameter(PARAM_ROLES_SEPARATOR), ";");
        this.useRolesNSuffix = "true".equals(getInitParameter(PARAM_USE_ROLES_NSUFFIX));
        this.commonRole = getInitParameter(PARAM_COMMON_ROLE);
        this.saveExtraHeaders = getInitParameter(PARAM_SAVE_EXTRA_HEADERS);*/
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
        if(request.getCookies() != null) {
            for(Cookie cookie: request.getCookies()) {
                if(COOKIE_NAME.equals(cookie.getName())) {
                    authCookie = cookie;
                }
            }
        }

        if(request.getRequestURI().startsWith(request.getContextPath() + logoutUrl)) {
            if(authCookie != null) {
                log.info("Clearing persisent login cookie for user " + authCookie.getValue() + " on logout");
                Cookie cookie = new Cookie(COOKIE_NAME, authCookie.getValue());
                cookie.setMaxAge(0);
                response.addCookie(cookie);
            }
            chain.doFilter(request, response);
            return;
        }

        if(!request.getRequestURI().startsWith(request.getContextPath() + persistentLoginPrefix)) {
            chain.doFilter(request, response);
            return;
        }

        Principal principal = request.getUserPrincipal();
        if(principal != null) {
            // If cookie already set, do nothing
            if(authCookie != null) {
                log.trace("Request external authenticated for user " + principal.getName() + ", cookie already set, path: " + request.getRequestURI());
                chain.doFilter(request, response);
                return;
            }

            Cookie cookie = new Cookie(COOKIE_NAME, principal.getName());
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
        } else  {

            if(authCookie != null) {
                // TODO check authCookie valid
                log.info("Using authentication from cookie for user " + authCookie.getValue() + ", saving principal in session: " + request.getRequestURI());

                // TODO get roles from DB
                persistentPrincipal = new PersistentAuthenticatedPrincipal(authCookie.getValue(), new HashSet(Arrays.asList(new String[] {"viewer"})));
                request.getSession().setAttribute(SESSION_PRINCIPAL, persistentPrincipal);
            } else {

                log.info("User not authenticated externally or by cooking, redirect tologin page");
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
