package nl.opengeogroep.safetymaps.server.stripes;

import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.UrlBinding;

/**
 *
 * @author matthijsln
 */
@UrlBinding("/viewer/api/login")
public class LoginActionBean implements ActionBean {
    private ActionBeanContext context;

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public Resolution redirect() {
        String returnTo = request.getParameter("returnTo");

        if (returnTo == null || returnTo.length() == 0) {
            returnTo = "/viewer/";
        }

        return new StreamingResolution("text/html",
            "<html><head>" +
                "<meta http-equiv=\"refresh\" content=\"0;url=" + context.getRequest().getContextPath() + returnTo + "\">" +
            "</head></html>"
        );
    }
}
