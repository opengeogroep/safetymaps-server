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
import net.sourceforge.stripes.action.StrictBinding;
import net.sourceforge.stripes.action.UrlBinding;
import net.sourceforge.stripes.validation.Validate;
import net.sourceforge.stripes.validation.ValidationErrorHandler;
import net.sourceforge.stripes.validation.ValidationErrors;
import nl.opengeogroep.safetymaps.server.db.DB;

import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;

/**
 *
 * @author Safety C&T
 */
@StrictBinding
@UrlBinding("/admin/action/maptrip")
public class MaptripActionBean implements ActionBean, ValidationErrorHandler {
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
  private String rowid;

  public String getRowid() {
    return rowid;
  }
  public void setRowid(String rowid) {
    this.rowid = rowid;
  }

  @Validate
  private String voertuignummer;

  public String getVoertuignummer() {
    return voertuignummer;
  }
  public void setVoertuignummer(String voertuignummer) {
    this.voertuignummer = voertuignummer;
  }

  @Validate
  private String maptriplicentie;

  public String getMaptriplicentie() {
    return maptriplicentie;
  }
  public void setMaptriplicentie(String maptriplicentie) {
    this.maptriplicentie = maptriplicentie;
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
    if (rowid != null) {
      Map<String,Object> data = DB.maptripQr().query("select * from broker.unit_devices where row_id = ?", new MapHandler(), Integer.parseInt(rowid));

      if(data.get("row_id") != null) {
        voertuignummer = data.get("safetyconnect_unit").toString();
        maptriplicentie = data.get("maptrip_device").toString();
      }
    }
    return new ForwardResolution(JSP);
  }

  public Resolution save() throws Exception {
    if (rowid == null) {
      DB.maptripQr().update("insert into broker.unit_devices(safetyconnect_unit, maptrip_device) values(?, ?)", voertuignummer, maptriplicentie);
    } else {
      DB.maptripQr().update("update broker.unit_devices set maptrip_device = ? where row_id=?", maptriplicentie, Integer.parseInt(rowid));
    }
    return cancel();
  }

  public Resolution delete() throws Exception {
    DB.maptripQr().update("delete from broker.unit_devices where row_id = ?", Integer.parseInt(rowid));
    return cancel();
  }

  @DontValidate
  public Resolution cancel() throws Exception {
    loadInfo();
    return new RedirectResolution(this.getClass()).flash(this);
  }

  @Override
  public Resolution handleValidationErrors(ValidationErrors errors) throws Exception {
      loadInfo();
      return new ForwardResolution(JSP);
  }

}
