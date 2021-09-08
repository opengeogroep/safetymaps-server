package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.naming.NamingException;
import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.UrlBinding;
import nl.opengeogroep.safetymaps.server.db.DB;
import org.apache.commons.dbutils.handlers.MapListHandler;

/**
 *
 * @author Matthijs Laan
 */
@UrlBinding("/admin/action/modules")
public class ModulesActionBean implements ActionBean {
    private ActionBeanContext context;

    private List<Map<String,Object>> modules = new ArrayList();

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public List<Map<String, Object>> getModules() {
        return modules;
    }

    public void setModules(List<Map<String, Object>> modules) {
        this.modules = modules;
    }

    @DefaultHandler
    public Resolution list() throws NamingException, SQLException {
        modules = DB.qr().query("select * from organisation.modules order by issmvngmodules desc, name asc", new MapListHandler());
        return null;
    }
}
