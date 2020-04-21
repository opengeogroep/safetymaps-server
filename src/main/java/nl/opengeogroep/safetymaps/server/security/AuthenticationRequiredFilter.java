package nl.opengeogroep.safetymaps.server.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 *
 * @author matthijsln
 */
public class AuthenticationRequiredFilter implements Filter {

    private FilterConfig filterConfig = null;

    private static final String PARAM_ALLOWED_ROLES = "allowedRoles";

    private List<String> allowedRoles;

    @Override
    public void init(FilterConfig filterConfig) {
        this.filterConfig = filterConfig;

        this.allowedRoles = Arrays.asList(ObjectUtils.firstNonNull(filterConfig.getInitParameter(PARAM_ALLOWED_ROLES), "").split(","));
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

        boolean authenticated = request.getUserPrincipal() != null;
        if(!authenticated) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authorization required");
        } else {
            if(!this.allowedRoles.isEmpty()) {
                boolean allowed = false;
                for(String role: allowedRoles) {
                    if(request.isUserInRole(role)) {
                        allowed = true;
                        break;
                    }
                }
                if(!allowed) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
                }
            }
        }

        chain.doFilter(request, response);
    }
}
