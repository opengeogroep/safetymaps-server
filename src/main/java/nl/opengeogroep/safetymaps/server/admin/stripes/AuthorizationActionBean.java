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
 * @author Safety C&T
 */
@UrlBinding("/admin/action/authorization")
public class AuthorizationActionBean implements ActionBean {
  private ActionBeanContext context;

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    private List<Map<String,Object>> authorizations = new ArrayList();

    public List<Map<String, Object>> getAuthorizations() {
        return authorizations;
    }

    public void setAuthorizations(List<Map<String, Object>> authorizations) {
        this.authorizations = authorizations;
    }

    @DefaultHandler
    public Resolution list() throws NamingException, SQLException {
      authorizations = DB.qr().query("select ur.username, r.* from safetymaps.user_roles ur inner join role r on r.role = ur.role order by 1 asc", new MapListHandler());
      return null;
    }
}
