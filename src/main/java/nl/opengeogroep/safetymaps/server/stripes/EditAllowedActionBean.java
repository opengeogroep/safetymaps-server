package nl.opengeogroep.safetymaps.server.stripes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.ErrorResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.UrlBinding;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_ADMIN;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_EDITOR;

/**
 *
 * @author matthijsln
 */
@UrlBinding("/viewer/editAllowed")
public class EditAllowedActionBean implements ActionBean {
    private ActionBeanContext context;

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public Resolution check() {
        HttpServletRequest request = getContext().getRequest();
        if(request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(ROLE_EDITOR)) {
            return new ErrorResolution(HttpServletResponse.SC_NOT_FOUND);
        } else {
            return new ErrorResolution(HttpServletResponse.SC_FORBIDDEN);
        }
    }

}
