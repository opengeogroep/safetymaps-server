package nl.opengeogroep.safetymaps.server.db;

import org.apache.commons.dbutils.handlers.ScalarHandler;

import javax.naming.NamingException;
import java.io.File;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static nl.opengeogroep.safetymaps.server.db.DB.qr;

/**
 *
 * @author Matthijs Laan
 */
public class Cfg {
    private static final long CACHE_FRESHNESS = 30 * 1000; // Cache setting values for 30 seconds

    static class CachedValue {
        private long timestamp;
        private Object value;

        public CachedValue(Object value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isFresh() {
            return System.currentTimeMillis() - this.timestamp < CACHE_FRESHNESS;
        }
    }

    private static Map<String,CachedValue> settingsCache = new ConcurrentHashMap<>();

    public static final void updateSetting(String name, Object value, String sql) throws NamingException, SQLException {
        qr().update("delete from safetymaps.settings where name=?", name);
        qr().update("insert into safetymaps.settings (name, value) values(?, " + (sql != null ? sql : "?") + ")", name, value);
        settingsCache.remove(name);
    }

    public static final String getSetting(String name) throws NamingException, SQLException {
        CachedValue cached = settingsCache.get(name);
        if(cached != null && cached.isFresh()) {
            return (String)cached.value;
        } else {
            settingsCache.remove(name);
        }

        String value = qr().query("select value from safetymaps.settings where name=?", new ScalarHandler<String>(), name);
        settingsCache.put(name, new CachedValue(value));
        return value;
    }

    public static final String getSetting(String name, String defaultValue) throws NamingException, SQLException {
        String s = getSetting(name);
        return s == null ? defaultValue : s;
    }

    public static final File getPath(String name) throws NamingException, SQLException {
        String v = (String)getSetting(name);
        if(v == null) {
            return null;
        }
        File f = new File(v);
        if(f.isAbsolute()) {
            return f;
        } else {
            String baseDir = (String)getSetting("basedir");
            return new File(baseDir + File.separator + v);
        }
    }

    public static final void settingsUpdated() throws NamingException, SQLException {
        updateSetting("last_config_update", new Date().getTime()/1000.0, "to_timestamp(?)");
    }

    public static final Date getLastUpdated() throws NamingException, SQLException {
        Timestamp timestamp = qr().query("select value::timestamp from safetymaps.settings where name='last_config_update'", new ScalarHandler<Timestamp>());
        return timestamp == null ? null : new Date(timestamp.getTime());
    }
}
