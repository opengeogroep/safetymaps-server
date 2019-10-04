package nl.opengeogroep.safetymaps.server.stripes;

import java.io.StringReader;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.Validate;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_ADMIN;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_EDITOR;

/**
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/viewer/api/edit")
public class EditActionBean  implements ActionBean {
    private ActionBeanContext context;

    @Validate
    private String features;

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public String getFeatures() {
        return features;
    }

    public void setFeatures(String features) {
        this.features = features;
    }

    @DefaultHandler
    public Resolution load() throws Exception {
        String edit = Cfg.getSetting("edit");
        if(edit == null) {
            edit = "{\"type\":\"FeatureCollection\",\"features\":[]}";
        }
        return new StreamingResolution("application/json", new StringReader(edit));
    }

    public Resolution save() throws Exception {
        HttpServletRequest request = getContext().getRequest();
        if(!request.isUserInRole(ROLE_ADMIN) && !request.isUserInRole(ROLE_EDITOR)) {
            return new ErrorResolution(HttpServletResponse.SC_FORBIDDEN);
        }
        Cfg.updateSetting("edit", features, null);
        return new StreamingResolution("application/json", new StringReader("{\"result\":true}"));
    }
}
