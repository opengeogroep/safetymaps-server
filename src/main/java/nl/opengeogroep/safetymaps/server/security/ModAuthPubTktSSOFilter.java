/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.server.security;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import static nl.opengeogroep.safetymaps.utils.SameSiteCookieUtil.addCookieWithSameSite;

/**
 * Generate ticket cookie for mod_auth_pubtkt (https://github.com/manuelkasper/mod_auth_pubtkt).
 *
 * Meant to be configured for filter path (such as /api/organisation.json) that is
 * regularly requested in order to refresh the cookie, we do not use the refresh
 * URL functionality of mod_auth_pubtkt.
 *
 * Can be used with external (webserver) authentication or container authentication.
 * No authorization / role check or multiple SSO cookies are currently implemented.
 *
 * Configured by database settings, needs context reload to change settings!
 *
 * @author matthijsln
 */
public class ModAuthPubTktSSOFilter implements Filter {

    private static final Log LOG = LogFactory.getLog(ModAuthPubTktSSOFilter.class);

    private static final String SESSION_COOKIE_EXPIRY = "mod_auth_pubtkt_cookie_expiry";

    /**
     * If the cookie/ticket is not still valid for this many seconds it is refreshed.
     */
    private static final long REFRESH_COOKIE_REMAINING_SECONDS = 5 * 60;

    private static ServletContext context;

    private boolean enabled = false;
    private PrivateKey privateKey;
    private String name = "auth_pubtkt";
    private String domain;
    private boolean assumeExternallyAuthenticated = false;
    private int validitySeconds = 10 * 60;

    @Override
    public void init(FilterConfig filterConfig) {

        /* Not configured by init parameters but by database settings */

        try {
            enabled = "true".equals(Cfg.getSetting("mod_auth_pubtkt_enabled"));
            if(!enabled) {
                return;
            }
            LOG.info("Loading mod_auth_pubtkt PKCS#8 private key...");

            String key = Cfg.getSetting("mod_auth_pubtkt_pkcs8_private_key");
            if(key != null) {
                privateKey = ModAuthPubTkt.getPrivateKey(key);
            } else {
                LOG.error("Private key not configured, disabled");
                enabled = false;
                return;
            }
            name = Cfg.getSetting("mod_auth_pubtkt_name", name);
            domain = Cfg.getSetting("mod_auth_pubtkt_domain");
            validitySeconds = Integer.parseInt(Cfg.getSetting("mod_auth_pubtkt_validity_seconds", validitySeconds + ""));
            assumeExternallyAuthenticated = "true".equals(Cfg.getSetting("mod_auth_pubtkt_externally_secured"));
            LOG.info("Filter enabled to set mod_auth_pubtkt SSO cookie with name " + name + " for domain " + domain + (assumeExternallyAuthenticated ? ", assuming secured externally by webserver / private network" : ", using container authentication"));
        } catch(Exception e) {
            LOG.error("Exception checking mod_auth_pubtkt config", e);
            enabled = false;
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest)servletRequest;
        HttpServletResponse response = (HttpServletResponse)servletResponse;
        HttpSession session = request.getSession();

        if(enabled) {
            try {
                addSSOCookie(request, response, privateKey, name, domain, validitySeconds, assumeExternallyAuthenticated);
            } catch(Exception e) {
                LOG.error("Exception adding SSO cookie, disabled", e);
                enabled = false;
            }
        }

        chain.doFilter(request, response);
    }

    public static void addSSOCookie(HttpServletRequest request, HttpServletResponse response, PrivateKey privateKey, String name, String domain, int validitySeconds, boolean assumeExternallyAuthenticated) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, UnsupportedEncodingException {
        String user = request.getRemoteUser();
        if(user == null) {
            if(!assumeExternallyAuthenticated) {
                return;
            } else {
                user = "external";
            }
        }

        Calendar validUntil = Calendar.getInstance();
        validUntil.add(Calendar.SECOND, validitySeconds);
        long cookieExpiry = validUntil.getTime().getTime() / 1000;

        Long previousCookieExpiry = (Long)request.getSession().getAttribute(SESSION_COOKIE_EXPIRY);
        if(previousCookieExpiry != null) {
            // Avoid refreshing cookie every request if still valid for 5 minutes
            long previousValidity = previousCookieExpiry - (System.currentTimeMillis() / 1000);
            if(previousValidity > REFRESH_COOKIE_REMAINING_SECONDS) {
                LOG.debug("Ticket still valid for " + previousValidity + "s, not refreshing");
                return;
            } else {
                if(previousValidity > 0) {
                    LOG.debug("Ticket only still valid for " + previousValidity + "s, refreshing");
                } else {
                    LOG.debug("Ticket was already expired for " + (-previousValidity) + "s, refreshing");
                }
            }
        }

        String ticket = "uid=" + user + ";validuntil=" + cookieExpiry;

        ticket += ";sig=" + ModAuthPubTkt.getSignature(ticket, privateKey);
        Cookie cookie = new Cookie(name, URLEncoder.encode(ticket, "US-ASCII"));
        cookie.setMaxAge(validitySeconds);
        cookie.setPath("/");
        cookie.setDomain(domain);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.getScheme().equals("https"));
        addCookieWithSameSite(response, cookie, "None");
        LOG.info("Added cookie with ticket for domain " + domain + " (SameSite=None) valid until " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(validUntil.getTime()));

        request.getSession().setAttribute(SESSION_COOKIE_EXPIRY, cookieExpiry);
    }

    @Override
    public void destroy() {
    }
}
