package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.naming.NamingException;
import net.sourceforge.stripes.action.*;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.DB;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/admin/action/sync")
public class SyncStatusActionBean implements ActionBean {

    private ActionBeanContext context;

    private static final Log log = LogFactory.getLog("syncstate");

    private static final String JNDI_NAME = "java:/comp/env/jdbc/filesetsync-server";

    private static final String JSP = "/WEB-INF/jsp/admin/sync.jsp";

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    private static QueryRunner qr() throws NamingException {
        return new QueryRunner(DB.getDataSource(JNDI_NAME));
    }

    @DefaultHandler
    public Resolution jsp() {
        return new ForwardResolution(JSP);
    }

    public Resolution json() throws NamingException, SQLException {
        // From client_startup, report with latest startup_time
        List<Map<String, Object>> rows = qr().query("select * "
                 + " from "
                 + "   (select *,row_number() over(partition by client_id,machine_id order by start_time desc) as rn "
                 + "    from client_startup) r "
                 + "where rn=1 "
                 + "order by start_time asc", new MapListHandler());

        List<Map<String,Object>> stateRows = qr().query("select * from client_state order by report_time desc", new MapListHandler());

        JSONArray stateA = new JSONArray();
        JSONArray startupA = new JSONArray();

        for(Map<String,Object> row: rows) {
            String clientId = (String)row.get("client_id");
            String machineId = (String)row.get("machine_id");
            JSONObject r = new JSONObject(row.get("report").toString());

            r.put("client_id", clientId);
            r.put("machine_id", machineId);
            r.put("start_time", ((java.sql.Timestamp)row.get("start_time")).getTime());

            for(Map<String,Object> stateRow: stateRows) {
                if(clientId.equals(stateRow.get("client_id")) && machineId.equals(stateRow.get("machine_id"))) {
                    JSONObject state = new JSONObject(stateRow.get("current_state").toString());
                    state.put("report_time", ((java.sql.Timestamp)stateRow.get("report_time")).getTime());
                    state.put("report_ip", stateRow.get("ip"));
                    r.put("current_state", state);
                    break;
                }
            }
            startupA.put(r);
        }

        for(Map<String,Object> row: stateRows) {
            JSONObject r = new JSONObject(row.get("current_state").toString());
            r.put("client_id", row.get("client_id"));
            r.put("machine_id", row.get("machine_id"));
            r.put("report_time", ((java.sql.Timestamp)row.get("report_time")).getTime());
            r.put("report_ip", row.get("ip"));
            stateA.put(r);
        }

        JSONObject response = new JSONObject();
        response.put("state", stateA);
        response.put("startup", startupA);

        return new ErrorMessageResolution(response);
    }
}
