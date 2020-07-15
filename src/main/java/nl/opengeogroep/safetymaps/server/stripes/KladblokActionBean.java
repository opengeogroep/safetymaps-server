package nl.opengeogroep.safetymaps.server.stripes;

import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.ErrorResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.StrictBinding;
import net.sourceforge.stripes.action.UrlBinding;
import net.sourceforge.stripes.validation.Validate;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.DB;
import static nl.opengeogroep.safetymaps.server.db.JSONUtils.rowToJson;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.dbutils.handlers.MapListHandler;
import org.json.JSONArray;

/**
 *
 * @author Bart Verhaar
 */
@StrictBinding
@UrlBinding("/viewer/api/kladblok/{incident}.json")
public class KladblokActionBean implements ActionBean {
    private ActionBeanContext context;

    @Validate
    private String incident;

    @Validate
    private String rule;

    @Validate
    private String vehicle;

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public String getIncident() {
        return incident;
    }

    public void setIncident(String incident) {
        this.incident = incident;
    }

    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
    }

    public String getVehicle() {
        return vehicle;
    }

    public void setVehicle(String vehicle) {
        this.vehicle = vehicle;
    }

    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_KLADBLOKCHAT_EDITOR = "kladblokchat_editor";

    @DefaultHandler
    public Resolution defaultHander() throws Exception {
        if("POST".equals(context.getRequest().getMethod())) {
            return save();
        } else {
            return load();
        }
    }

    public Resolution load() throws Exception {
        JSONArray response = new JSONArray();

        try {
            List<Map<String,Object>> results = DB.qr().query("select rule, vehicle, created from safetymaps.kladblok where incident = ?", new MapListHandler(), incident);

            for (Map<String, Object> resultRow : results) {
                response.put(rowToJson(resultRow, false, false));
            }

            return new StreamingResolution("application/json", response.toString());
        } catch(Exception e) {
            return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error: " + e.getClass() + ": " + e.getMessage());
        }
    }

    public Resolution save() throws Exception {
        HttpServletRequest request = getContext().getRequest();
        
        Object[] qparams = new Object[] {
            incident,
            new Date(),
            vehicle,
            rule
        };

        if(!request.isUserInRole(ROLE_ADMIN) && !request.isUserInRole(ROLE_KLADBLOKCHAT_EDITOR)) {
            return new ErrorResolution(HttpServletResponse.SC_FORBIDDEN);
        }

        try {
            DB.qr().insert("insert into safetymaps.kladblok (incident, created, vehicle, rule) values (?,?,?,?)", new MapListHandler(), qparams);
            return new ErrorMessageResolution(200, "");
        } catch(Exception e) {
            return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error: " + e.getClass() + ": " + e.getMessage());
        }
    }
}