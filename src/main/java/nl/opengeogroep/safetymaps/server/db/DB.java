package nl.opengeogroep.safetymaps.server.db;

import java.sql.Connection;
import java.sql.SQLException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author Matthijs Laan
 */
public class DB {
    private static final Log log = LogFactory.getLog("db");

    private static final String JNDI_NAME = "java:/comp/env/jdbc/safetymaps-server";
    private static final String JNDI_NAME_BAG = "java:/comp/env/jdbc/nlextract-bag";

    public static final String USER_TABLE = "safetymaps.user_ ";
    public static final String USER_ROLE_TABLE = "safetymaps.user_roles ";
    public static final String ROLE_TABLE = "safetymaps.role ";
    public static final String SESSION_TABLE = "safetymaps.persistent_session ";

    public static final String ROLE_ADMIN = "admin";
    public static final String USER_ADMIN = "admin";
    public static final String ROLE_EDITOR = "editor";

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
}
