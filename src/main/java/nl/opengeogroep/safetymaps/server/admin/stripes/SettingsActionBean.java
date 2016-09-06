package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.naming.NamingException;
import net.sourceforge.stripes.action.*;
import nl.opengeogroep.safetymaps.server.db.DB;
import org.apache.commons.dbutils.handlers.MapListHandler;

/**
 *
 * @author Matthijs Laan
 */
public class SettingsActionBean implements ActionBean {
    private ActionBeanContext context;

    private final Map<String,String> settings = new HashMap();

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public Map<String, String> getSettings() {
        return settings;
    }

    @DefaultHandler
    public Resolution list() throws NamingException, SQLException {
        List<Map<String,Object>> rl = DB.qr().query("select * from safetymaps.settings order by name", new MapListHandler());

        for(Map<String,Object> s: rl) {
            settings.put((String)s.get("name"), (String)s.get("value"));
        }

        return null;
    }
}
