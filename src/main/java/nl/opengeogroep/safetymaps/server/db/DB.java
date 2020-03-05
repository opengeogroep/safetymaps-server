package nl.opengeogroep.safetymaps.server.db;

import java.sql.Connection;
import java.sql.SQLException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;

/**
 *
 * @author Matthijs Laan
 */
public class DB {
    private static final Log log = LogFactory.getLog("db");

    private static final String JNDI_NAME = "java:/comp/env/jdbc/safetymaps-server";
    private static final String JNDI_NAME_BAG = "java:/comp/env/jdbc/nlextract-bag";
    private static final String JNDI_NAME_OIV = "java:/comp/env/jdbc/oiv";

    public static final String USER_TABLE = "safetymaps.user_ ";
    public static final String USER_ROLE_TABLE = "safetymaps.user_roles ";
    public static final String ROLE_TABLE = "safetymaps.role ";
    public static final String SESSION_TABLE = "safetymaps.persistent_session ";

    public static final String ROLE_ADMIN = "admin";
    public static final String USER_ADMIN = "admin";
    public static final String ROLE_EDITOR = "editor";
    public static final String ROLE_INCIDENTMONITOR = "incidentmonitor";
    public static final String ROLE_INCIDENTMONITOR_KLADBLOK = "incidentmonitor_kladblok";
    public static final String ROLE_EIGEN_VOERTUIGNUMMER = "eigen_voertuignummer";
    public static final String ROLE_DRAWING_EDITOR = "drawing_editor";

    public static final String USERNAME_LDAP = "ldap_gebruiker";

    public static final DataSource getDataSource(String jndiName) throws NamingException {
        InitialContext cxt = new InitialContext();
        log.trace("looking up JNDI resource " + jndiName);
        DataSource ds = (DataSource)cxt.lookup(jndiName);
        if(ds == null) {
            throw new NamingException("Data source " + jndiName + " not found, please configure the webapp container correctly according to the installation instructions");
        }
        return ds;
    }

    public static final Connection getConnection() throws NamingException, SQLException {
        return getDataSource(JNDI_NAME).getConnection();
    }

    public static final QueryRunner qr() throws NamingException {
        return new QueryRunner(getDataSource(JNDI_NAME));
    }

    public static final QueryRunner bagQr() throws NamingException {
        return new QueryRunner(getDataSource(JNDI_NAME_BAG));
    }

    public static final QueryRunner oivQr() throws NamingException {
        return new QueryRunner(getDataSource(JNDI_NAME_OIV));
    }
    
    public static final JSONObject getUserDetails(HttpServletRequest request, Connection c) throws Exception {
        String username = request.getRemoteUser();
        if(username != null) {
            return getUserDetails(request.getRemoteUser(), c);
        } else {
            return new JSONObject();
        }
    }

    public static final JSONObject getUserDetails(String username, Connection c) throws Exception {
        Object d = new QueryRunner().query(c, "select details from " + USER_TABLE + " where username = ?", new ScalarHandler<>(), username);
        if(d != null) {
            return new JSONObject(d.toString());
        } else {
            return new JSONObject();
        }
    }
}
