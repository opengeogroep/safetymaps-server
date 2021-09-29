package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.naming.NamingException;
import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.Before;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.ForwardResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.UrlBinding;
import nl.opengeogroep.safetymaps.server.db.DB;
import org.apache.commons.dbutils.handlers.MapListHandler;

/**
 *
 * @author Safety C&T
 */
@UrlBinding("/admin/action/maptrip")
public class MaptripActionBean implements ActionBean {
  private ActionBeanContext context;

  private static final String JSP = "/WEB-INF/jsp/admin/maptrip.jsp";

  @Override
  public ActionBeanContext getContext() {
      return context;
  }
  @Override
  public void setContext(ActionBeanContext context) {
      this.context = context;
  }

  private List<Map<String,Object>> units = new ArrayList();
  public List<Map<String, Object>> getUnits() {
      return units;
  }
  public void setUnits(List<Map<String, Object>> units) {
      this.units = units;
  }

  @Before
  private void loadInfo() throws NamingException, SQLException {
    units = DB.maptripQr().query("select * from broker.unit_devices order by safetyconncet_unit", new MapListHandler());
  }

  @DefaultHandler
  public Resolution list() throws NamingException, SQLException {
    return new ForwardResolution(JSP);
  }
}
