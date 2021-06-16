package nl.opengeogroep.safetymaps.server.stripes;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
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

        String returnTo = context.getRequest().getParameter("returnTo");
        if (returnTo == null) {
            returnTo = "/viewer/";
        }
        returnTo = URLEncoder.encode(returnTo, "UTF-8");

        String url = "/viewer/api/login?returnTo=" + returnTo;

        // Check if SSO is configured/enabled
        if(ssoPassiveUrl != null) {

            // Redirect only once per browser session.
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

            if(cookie == null) {
                cookie = new Cookie(COOKIE_NAME, "true");
                cookie.setHttpOnly(false);
                cookie.setSecure(context.getRequest().getScheme().equals("https"));
                getContext().getResponse().addCookie(cookie);

                url = ssoPassiveUrl;

                HttpServletRequest request = getContext().getRequest();
                String returnUrl = new URL(request.getScheme(), request.getServerName(), request.getContextPath() + "/auth/saml?returnTo=" + returnTo).toString();
                String returnUrlParam = URLEncoder.encode(returnUrl, "UTF-8");
                url = url.replace("[returnUrl]", returnUrlParam);
            }
        }

        return new RedirectResolution(url);
    }
}
