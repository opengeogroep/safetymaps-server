package nl.opengeogroep.safetymaps.server.stripes;

import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.StrictBinding;
import net.sourceforge.stripes.action.UrlBinding;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import org.json.JSONObject;

/**
 * API that returns configuration for logging in, no login session required.
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/api/login-config")
public class LoginConfigActionBean implements ActionBean {

    private ActionBeanContext context;

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public Resolution config() {

        JSONObject config = new JSONObject();
        try {
            config.put("ssoPassiveUrl", context.getRequest().getContextPath() + Cfg.getSetting("sso_passive_url"));
            String url = Cfg.getSetting("sso_manual_html");
            if(url != null) {
                url = url.replace("[contextPath]", context.getRequest().getContextPath());
            }
            config.put("ssoManualHtml", url);
        } catch(Exception e) {
        }

        return new StreamingResolution("application/json", config.toString());
    }
}
