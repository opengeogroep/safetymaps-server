package nl.opengeogroep.safetymaps.server.stripes;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Date;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.RedirectResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.UrlBinding;
import nl.opengeogroep.safetymaps.server.db.Cfg;

/**
 *
 * @author matthijsln
 */
@UrlBinding("/api/sso")
public class SSOActionBean implements ActionBean {
    private ActionBeanContext context;

    private static final String COOKIE_NAME = "sm-saml2-passive-tried";

    private static final long SSO_PASSIVE_TIMER = 5 * 60 * 1000;

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public Resolution redirect() throws MalformedURLException, UnsupportedEncodingException {

        String ssoPassiveUrl = null;
        try {
            ssoPassiveUrl = Cfg.getSetting("sso_passive_url");
        } catch(Exception e) {
        }

        String url = "/viewer/api/login";

        // Check if SSO is configured/enabled
        if(ssoPassiveUrl != null) {

            // Set a cookie with the time the SSO redirect was tried so it can
            // be done only once per timeout. When a user suspends his session
            // and restarts it later, the browser and mellon session may be expired
            // but a passive login may succeed again.

            // We don't want to redirect every time, because when a SSO error
            // occurs (IdP offline, configuration issue) a user still needs to be
            // able to login with a safetymaps account.

            // We use a cookie instead of a session attribute here, because that
            // would get cleared after logout.

            Cookie cookie = null;
            if(getContext().getRequest().getCookies() != null) {
                for(Cookie c: getContext().getRequest().getCookies()) {
                    if(COOKIE_NAME.equals(c.getName())) {
                        cookie = c;
                    }
                }
            }

            boolean trySso = cookie == null;

            // Check if SSO was tried more than a timeout ago, so it can be tried
            // again in this browser session
            if(cookie != null) {
                try {
                    Date lastSsoTried = new Date(Long.parseLong(cookie.getValue()));

                    trySso = new Date().getTime() - lastSsoTried.getTime() > SSO_PASSIVE_TIMER;
                } catch(Exception e) {
                }
            }

            if(trySso) {
                cookie = new Cookie(COOKIE_NAME, new Date().getTime() + "");
                cookie.setHttpOnly(false);
                cookie.setSecure(context.getRequest().getScheme().equals("https"));
                getContext().getResponse().addCookie(cookie);

                url = ssoPassiveUrl;

                HttpServletRequest request = getContext().getRequest();
                String returnUrl = new URL(request.getScheme(), request.getServerName(), request.getContextPath() + "/auth/saml?returnTo=/viewer/").toString();
                String returnUrlParam = URLEncoder.encode(returnUrl, "UTF-8");
                url = url.replace("[returnUrl]", returnUrlParam);
            }
        }

        return new RedirectResolution(url);
    }
}
