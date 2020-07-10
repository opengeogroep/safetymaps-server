package nl.opengeogroep.safetymaps.server.security;

import nl.opengeogroep.safetymaps.server.db.Cfg;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author matthijsln
 */
public class CorsFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }

    @Override
    public String toString() {
        return "CorsFilter";
    }

    public static void addCorsHeaders(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            response.addHeader("Access-Control-Allow-Credentials", "true");
            // This is needed for the DrawingActionBean because the drawing module sends the If-Modified-Since header
            // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Access-Control-Allow-Headers
            response.addHeader("Access-Control-Allow-Headers", Cfg.getSetting("cors_allowed_headers", "If-Modified-Since"));
            response.addHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");

            String allowedOrigins[] = Cfg.getSetting("cors_allowed_origins", "http://localhost,http://192.168.100.2").split(",");

            String origin = request.getHeader("Origin");
            if (origin != null) {
                for (String allowedOrigin: allowedOrigins) {
                    if (allowedOrigin.equalsIgnoreCase(origin)) {
                        response.addHeader("Access-Control-Allow-Origin", origin);

                        break;
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException("Error getting settings", e);
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest)servletRequest;
        HttpServletResponse response = (HttpServletResponse)servletResponse;

        addCorsHeaders(request, response);

        if("OPTIONS".equals(request.getMethod())) {
            response.setStatus(200);
        } else {
            chain.doFilter(request, response);
        }
    }
}
