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

    public static final DataSource getDataSource() throws NamingException {
        InitialContext cxt = new InitialContext();
        log.trace("looking up JNDI resource " + JNDI_NAME);
        DataSource ds = (DataSource)cxt.lookup(JNDI_NAME);
        if(ds == null) {
            throw new NamingException("Data source " + JNDI_NAME + " not found, please configure the webapp container correctly according to the installation instructions");
        }
        return ds;
    }

    public static final Connection getConnection() throws NamingException, SQLException {
        return getDataSource().getConnection();
    }

    public static final QueryRunner qr() throws NamingException {
        return new QueryRunner(getDataSource());
    }
}