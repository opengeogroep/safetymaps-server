package nl.opengeogroep.safetymaps.server.security;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Based on https://github.com/flamingo-geocms/flamingo/blob/master/web-commons/src/main/java/nl/b3p/web/filter/HeaderAuthenticationFilter.java
 *
 * That file is licensed under AGPL but has been dual-licensed by the full
 * copyright holder (B3Partners B.V.) to GPL for this project.
 *
 * Servlet filter which trusts authentication headers set by mod_auth_mellon and
 * not by end users (the webserver MUST be configured with a secure header name
 * prefix to avoid security problems).
 * <p>
 * This filter trusts HTTP request headers, which must be set by Apache on the
 * configured authPath. Pick a random header prefix to prevent a user from
 * maliciously providing the headers directly to the HTTP connector.
 * </p>
 * <h2>Usage with mod_auth_mellon for SAML support</h2>
 * Configure Mellon as follows:
 * <pre>
 * &lt;Location /&gt;
 *     MellonEndpointPath "/mellon"
 *     MellonSPPrivateKeyFile mellon/sp-private-key.pem
 *     MellonSPCertFile mellon/sp-cert.pem
 *     # Download de metadata van de IdP naar onderstaand bestand, voor ADFS iets als https://adfs.organisatie.nl/FederationMetadata/2007-06/FederationMetadata.xml
 *     MellonIdpMetadataFile mellon/idp-metadata-here.xml
 *
 *     # In de SP metadata file kan de NameIDPolicy in het SAMLRequest aangepast worden,
 *     # standaard is transient
 *     #MellonSPMetadataFile /etc/apache2/mellon/sp-metadata.xml
 *
 *     MellonNoSuccessErrorPage /viewer/mellonerror.jsp
 * &lt;/Location&gt;
 *
 * &lt;Location [contextPath]/auth/saml&gt;
 *     Require valid-user
 *     AuthType "Mellon"
 *     MellonEnable auth
 *
 *     # Kijk welke attributen er zijn door test met Location /[contextPath]/authinfo.jsp
 *     # en de [prefix]_SESSION header te pasten in https://www.samltool.com/decode.php
 *
 *     # Gebruik voor RequestHeader altijd een unieke prefix, zodat een hacker niet
 *     # kan inloggen door zelf Mellon headers te sturen naar [contextPath]auth/saml
 *
 *     #MellonSessionDump On
 *     #RequestHeader set [prefix]_SESSION "%{MELLON_SESSION}e"
 *     #MellonSamlResponseDump On
 *
 *     #MellonSetEnvNoPrefix "MELLON_uid" "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/upn"
 *     #RequestHeader set [prefix]_uid "%{MELLON_uid}e"
 *     RequestHeader set [prefix]_uid "%{MELLON_NAME_ID}e"
 *
 *     MellonMergeEnvVars On
 *     MellonSetEnvNoPrefix "MELLON_roles" "http://schemas.xmlsoap.org/claims/Group"
 *     RequestHeader set [prefix]_roles "%{MELLON_roles}e"
 *  &lt;/Location&gt;
 *  # Dit is nodig voor de [prefix]_SESSION header! Pas ook de packet size aan in AJP Connector in Tomcat server.xml
 *  ProxyIOBufferSize 65536*
 * </pre>
 * <h2>Logging out</h2>
 * Not currently supported. To enable logout by IdP calling the SingleLogoutService
 * binding, we would need to enable MellonEnable info for all URL's and check if
 * the userHeader is still there when the principal is on the session attribute.
 * SP initiated logout also not supported, would need extra configuration of the
 * SingleLogoutService binding in the IdP metadata.
 *
 * @author Matthijs Laan
 */
public class MellonHeaderAuthenticationFilter implements Filter {

    private static final Log log = LogFactory.getLog(MellonHeaderAuthenticationFilter.class);

    private FilterConfig filterConfig = null;

    private static final String CFG_HEADER_PREFIX = "sso_mellon_header_prefix";

    /**
     * Prefix for context global parameters which can be set to override filter
     * init params for easier deployments without overwriting web.xml.
     */
    private static final String CONTEXT_PARAM_PREFIX = "headerAuth";

    /**
     * Random header prefix which must be kept secret and changed on each
     * deployment. If this is not changed from the default, this filter is not
     * enabled.
     */
    public static final String PARAM_HEADER_PREFIX = "prefix";

    /**
     * userHeader init-param: the request header that contains
     * the username, default _uid.
     */
    public static final String PARAM_USER_HEADER = "userHeader";

    /**
     * authPath init-param: path after the contextPath for which Apache is
     * configured to send the authentication/authorization headers which we
     * trust - must override any headers sent by the client, default
     * &quot;/auth/saml&quot;. If the application directly redirects to this
     * path without redirecting to authInitPath first, redirects to the
     * contextPath after succesful authentication.
     */
    public static final String PARAM_AUTH_PATH = "authPath";

    /**
     * authInitPath init-param: path which will save a returnTo parameter or
     * Referer before redirecting to the authPath, default
     * &quot;/auth/init&quot;. The returnTo parameter is saved in the session
     * and redirected to after successful login. Only relative URL's supported,
     * otherwise redirects to the contextPath. When no returnTo parameter is
     * present the Referer header is saved and redirected to after successful
     * login.
     */
    public static final String PARAM_AUTH_INIT_PATH = "authInitPath";

    /**
     * rolesHeader init-param: header which contains the roles, defaults to
     * &quot;_roles&quot;.
     */
    public static final String PARAM_ROLES_HEADER = "rolesHeader";

    /**
     * commonRole init-param: role to always add to users authenticated by this
     * filter.
     */
    public static final String PARAM_COMMON_ROLE = "commonRole";

    /**
     * saveExtraHeaders init-param: extra headers to save sent to authPath, such
     * as [prefix]_SESSION, separated by &quot;,&quot;. Retrieve using
     * getExtraAuthHeaders().
     */
    public static final String PARAM_SAVE_EXTRA_HEADERS = "saveExtraHeaders";

    private static final String ATTR_RETURN_TO = MellonHeaderAuthenticationFilter.class.getName() + ".RETURN_TO";
    private static final String ATTR_PRINCIPAL = MellonHeaderAuthenticationFilter.class.getName() + ".PRINCIPAL";
    private static final String ATTR_EXTRA_HEADERS = MellonHeaderAuthenticationFilter.class.getName() + ".EXTRA_HEADERS";

    private String userHeader;
    private String authPath;
    private String authInitPath;
    private String rolesHeader;
    private String commonRole;
    private String saveExtraHeaders;

    public MellonHeaderAuthenticationFilter() {
    }

    /**
     * Get a filter init-parameter which can be overriden by a context parameter
     * when prefixed with "headerAuth".
     */
    private String getInitParameter(String paramName) {
        String contextParamName = CONTEXT_PARAM_PREFIX + StringUtils.capitalize(paramName);
        String value = filterConfig.getServletContext().getInitParameter(contextParamName);
        if(value == null) {
            value = filterConfig.getInitParameter(paramName);
            log.debug("Using filter init parameter " + paramName + ": " + (PARAM_HEADER_PREFIX.equals(paramName) ? "<hidden for security reasons>" : value));
        } else {
            log.debug("Using context parameter " + contextParamName + ": " + (PARAM_HEADER_PREFIX.equals(paramName) ? "<hidden for security reasons>" : value));
        }
        return value;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        this.filterConfig = filterConfig;

        this.userHeader = ObjectUtils.firstNonNull(getInitParameter(PARAM_USER_HEADER), "_uid");
        this.authPath = ObjectUtils.firstNonNull(getInitParameter(PARAM_AUTH_PATH), "auth/saml");
        this.authInitPath = ObjectUtils.firstNonNull(getInitParameter(PARAM_AUTH_PATH), "auth/init");
        this.rolesHeader = ObjectUtils.firstNonNull(getInitParameter(PARAM_ROLES_HEADER), "_roles");
        this.commonRole = getInitParameter(PARAM_COMMON_ROLE);
        this.saveExtraHeaders = getInitParameter(PARAM_SAVE_EXTRA_HEADERS);
        log.info("Initialized - " + toString());
    }

    @Override
    public void destroy() {
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    private String readHeaderPrefixCfgFromDb() {
        try {
            return Cfg.getSetting(CFG_HEADER_PREFIX);
        } catch(Exception e) {
            return null;
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest)servletRequest;
        HttpServletResponse response = (HttpServletResponse)servletResponse;
        HttpSession session = request.getSession();

        if(request.getUserPrincipal() != null) {
            if(request.getRequestURI().equals(request.getContextPath() + "/" + authInitPath)) {
                log.warn("Login requested but already authenticated by servlet container!");
                session.invalidate();
            }

            // Do nothing when using tomcatAuthentication=false or authenticated
            // by other means such as standard servlet login-config
            if(log.isTraceEnabled()) {
                log.trace("Already authenticated as user " + request.getRemoteUser() + " (principal " + request.getUserPrincipal() + "), passing through " + request.getRequestURI());
            }

            chain.doFilter(request, servletResponse);
            return;
        }

        if(request.getRequestURI().equals(request.getContextPath() + "/" + authInitPath)) {

            String headerPrefix = readHeaderPrefixCfgFromDb();

            if(headerPrefix != null) {
                // Save the returnTo parameter or the Referer header, only accept
                // relative path for returnTo parameter
                String returnTo = request.getParameter("returnTo");
                String msg;
                if(returnTo == null || !returnTo.startsWith(request.getContextPath())) {
                    // Try Referer header
                    returnTo = request.getHeader("Referer");
                    if(returnTo != null) {
                        msg = ", redirecting to this Referer after successful login: " + returnTo;
                    } else {
                        msg = ", no relative returnTo parameter or Referer header, redirecting to contextPath after succesful login";
                    }
                } else {
                    msg = ", redirecting to returnTo parameter after successful login: " + returnTo;
                }
                session.setAttribute(ATTR_RETURN_TO, returnTo);

                String isPassiveParam = "";
                String isPassive = request.getParameter("IsPassive");
                if(isPassive != null) {
                    isPassiveParam = "?IsPassive=" + isPassive;
                }

                log.info("Redirecting to authPath " + authPath + msg);
                response.sendRedirect(request.getContextPath() + "/" + authPath + isPassiveParam);
                return;
            }
        }

        if(request.getRequestURI().equals(request.getContextPath() + "/" + authPath)) {
            String headerPrefix = readHeaderPrefixCfgFromDb();

            if(headerPrefix != null) {
                // Must be protected by Apache auth module!

                // Check for user header
                String user = request.getHeader(headerPrefix + userHeader);
                if(user == null) {
                    log.warn("No user header returned, Apache should have denied access!");
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Not authorized by identity provider");
                    return;
                }

                Set<String> roles = new HashSet<>();
                if(commonRole != null) {
                    roles.add(commonRole);
                }
                String r = request.getHeader(headerPrefix + rolesHeader);
                if(r != null) {
                    roles.addAll(Arrays.asList(r.split(Pattern.quote(";"))));
                }

                log.info("Authenticated user from header [prefix]" + userHeader + ": " + user + ", roles: " + roles);

                session.setAttribute(ATTR_PRINCIPAL, new HeaderAuthenticatedPrincipal(user, roles));

                Map<String,String> extraHeaders = new HashMap();
                session.setAttribute(ATTR_EXTRA_HEADERS, extraHeaders);
                if(saveExtraHeaders != null) {
                    for(String h: saveExtraHeaders.split(",")) {
                        String value = request.getHeader(headerPrefix + h);
                        if(value != null) {
                            extraHeaders.put(h, value);
                        }
                    }
                    if(!saveExtraHeaders.isEmpty()) {
                        log.info("Extra headers saved from auth request: " + extraHeaders);
                    }
                }

                String returnTo = request.getParameter("returnTo");
                if(returnTo != null) {
                    // When directly logged in using a redirect to /mellon/login
                    // from outside the filter, accept a returnTo URL parameter.
                    // This can be used for AutnRequests with IsPassive=true.

                    // XXX authInitPath now passes IsPassive URL through

                    log.info("Redirecting to returnTo from URL parameter: " + returnTo);
                    response.sendRedirect(returnTo);
                } else {
                    returnTo = (String)session.getAttribute(ATTR_RETURN_TO);
                    if(returnTo != null) {
                        log.info("Redirecting after successful login to: " + returnTo);
                        response.sendRedirect(returnTo);
                    } else {
                        log.info("Redirecting to default page after successful login");
                        response.sendRedirect(request.getContextPath() + "/");
                    }
                }
                return;
            }
        }

        final HeaderAuthenticatedPrincipal principal = (HeaderAuthenticatedPrincipal)session.getAttribute(ATTR_PRINCIPAL);
        if(principal != null) {
            if(log.isTraceEnabled()) {
                log.trace("Chaining authenticated request for user " + principal.getName() + " for URL " + request.getRequestURL());
            }
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
                    return principal.isUserInRole(role);
                }
            }, response);
        } else {
            if(log.isTraceEnabled()) {
                log.trace("Chaining unauthenticated request for URL " + request.getRequestURL());
            }
            chain.doFilter(request, response);
        }
    }

    public static Map<String, String> getExtraHeaders(HttpServletRequest request) {
        return (Map<String, String>)request.getSession().getAttribute(ATTR_EXTRA_HEADERS);
    }

    private class HeaderAuthenticatedPrincipal implements Principal {
        private final String name;
        private final Set<String> roles;

        public HeaderAuthenticatedPrincipal(String name, Set<String> roles) {
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