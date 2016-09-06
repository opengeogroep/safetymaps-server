package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.naming.NamingException;
import net.sourceforge.stripes.action.*;
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
                 + "   (select *,row_number() over(partition by client_id order by start_time desc) as rn "
                 + "    from client_startup) r "
                 + "left join client_state cs on (cs.client_id = r.client_id) "
                 + "where rn=1 "
                 + "order by start_time asc", new MapListHandler());

        JSONArray clients = new JSONArray();

        for(Map<String,Object> row: rows) {
            JSONObject j = new JSONObject();
            j.put("client_id", (String)row.get("client_id"));
            j.put("start_time", ((java.sql.Timestamp)row.get("start_time")).getTime());
            j.put("start_report", new JSONObject(row.get("report").toString()));
            Object reportTime = row.get("report_time");
            j.put("state_time", reportTime == null ? null : ((java.sql.Timestamp)reportTime).getTime());
            j.put("state_ip", (String)row.get("ip"));
            Object currentState = row.get("current_state");
            j.put("state", currentState == null ? null : new JSONObject(currentState.toString()));
            clients.put(j);
        }

        return new StreamingResolution("application/json", clients.toString());
    }
}
