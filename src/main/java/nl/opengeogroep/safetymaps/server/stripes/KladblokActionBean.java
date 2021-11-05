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

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.dbutils.handlers.MapListHandler;
import org.json.JSONArray;

import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_ADMIN;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_KLADBLOKCHAT_EDITOR;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_KLADBLOKCHAT_EDITOR_GMS;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_KLADBLOKCHAT_VIEWER;

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
    private String row;

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

    public String getRow() {
        return row;
    }

    public void setRow(String row) {
        this.row = row;
    }

    public String getVehicle() {
        return vehicle;
    }

    public void setVehicle(String vehicle) {
        this.vehicle = vehicle;
    }

    @DefaultHandler
    public Resolution defaultHander() throws Exception {
        if("POST".equals(context.getRequest().getMethod())) {
            return save();
        } else {
            return load();
        }
    }

    public Resolution load() throws Exception {
        HttpServletRequest request = getContext().getRequest();
        JSONArray response = new JSONArray();

        if(!request.isUserInRole(ROLE_ADMIN) && !request.isUserInRole(ROLE_KLADBLOKCHAT_VIEWER) && !request.isUserInRole(ROLE_KLADBLOKCHAT_EDITOR) && !request.isUserInRole(ROLE_KLADBLOKCHAT_EDITOR_GMS)) {
            return new ErrorResolution(HttpServletResponse.SC_FORBIDDEN);
        }

        try {
            List<Map<String,Object>> results = DB.qr().query("select to_char(dtg, 'YYYY-MM-DD HH24:MI:SS') as DTG, case when coalesce(s.value, 'false') = 'false' then '(' || COALESCE(vehicle, username) || ') ' || inhoud when coalesce(s.value, 'false') = 'true' then '(' || username || ') ' || inhoud end as Inhoud from safetymaps.kladblok k left join safetymaps.settings s on s.name = 'show_user_in_chat' where incident = ?", new MapListHandler(), incident);

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

        if(!request.isUserInRole(ROLE_ADMIN) && !request.isUserInRole(ROLE_KLADBLOKCHAT_EDITOR) && !request.isUserInRole(ROLE_KLADBLOKCHAT_EDITOR_GMS)) {
            return new ErrorResolution(HttpServletResponse.SC_FORBIDDEN);
        }

        if(row.length() > 0 && row.length() <= 500) {
            String username = getContext().getRequest().getRemoteUser();
            String user = username.split("@")[0];
            int length = user.length() > 10 ? 10 : user.length();

            username = user.substring(0, length);

            try {
                DB.qr().insert("insert into safetymaps.kladblok (incident, dtg, inhoud, username, vehicle) values (?,?,?,?,?)", new MapListHandler(), 
                    incident, new java.sql.Timestamp(System.currentTimeMillis()), row, username, vehicle);

                return new ErrorMessageResolution(200, "");
            } catch(Exception e) {
                return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error: " + e.getClass() + ": " + e.getMessage());
            }
        } else {
            return new ErrorMessageResolution(HttpServletResponse.SC_FORBIDDEN, "Notepad row must contain at least 1 character and no more then 500 characters.");
        }
    }
}
