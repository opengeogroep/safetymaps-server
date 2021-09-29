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
import net.sourceforge.stripes.action.DontValidate;
import net.sourceforge.stripes.action.ForwardResolution;
import net.sourceforge.stripes.action.RedirectResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.UrlBinding;
import net.sourceforge.stripes.validation.Validate;
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

  @Validate
  private Number rowId;

  public Number getRowId() {
    return rowId;
  }
  public void setRowId(Number value) {
    this.rowId = value;
  }

  @Validate
  private String voertuignummer;

  public String getVoertuignummer() {
    return voertuignummer;
  }
  public void setVoertuignummer(String value) {
    this.voertuignummer = value;
  }

  @Validate
  private String maptriplicentie;
  
  public String getMaptriplicentie() {
    return maptriplicentie;
  }
  public void setMaptriplicentie(String value) {
    this.maptriplicentie = value;
  }

  @Before
  private void loadInfo() throws NamingException, SQLException {
    units = DB.maptripQr().query("select * from broker.unit_devices order by safetyconnect_unit", new MapListHandler());
  }

  @DefaultHandler
  public Resolution list() throws NamingException, SQLException {
    return new ForwardResolution(JSP);
  }

  public Resolution edit() throws SQLException, NamingException {
    return new ForwardResolution(JSP);
  }

  public Resolution save() throws Exception {
    if (rowId == null) {
      DB.maptripQr().update("insert into broker.unit_devices(safetyconnect_unit, maptrip_device) values(?, ?)", voertuignummer, maptriplicentie);
    } else {
      DB.maptripQr().update("update broker.unit_devices set maptrip_device = ? where row_id=?", maptriplicentie, rowId);
    }
    return cancel();
  }

  public Resolution delete() throws Exception {
    DB.maptripQr().update("delete from broker.unit_devices where row_id = ?", rowId);
    return cancel();
  }

  @DontValidate
  public Resolution cancel() throws Exception {
    loadInfo();
    return new RedirectResolution(this.getClass()).flash(this);
  }
}
