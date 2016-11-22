package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.io.StringReader;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.Validate;
import nl.opengeogroep.safetymaps.server.db.Cfg;

/**
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/action/api/edit")
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
        Cfg.updateSetting("edit", features, null);
        return new StreamingResolution("application/json", new StringReader("{\"result\":true}"));
    }
}
