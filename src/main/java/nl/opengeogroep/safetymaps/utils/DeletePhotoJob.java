/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.utils;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.NamingException;
import nl.opengeogroep.safetymaps.server.db.DB;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 *
 * @author martijn
 */
public class DeletePhotoJob implements Job {
    
    private static final Log LOG = LogFactory.getLog(DeletePhotoJob.class);
    
    private final int INTERVAL = 7;

    @Override
    public void execute(JobExecutionContext jec) throws JobExecutionException {
        try {
            // get photo directory from DB
            Map<String, Object> settings = DB.qr().query("select value from safetymaps.settings where name='fotofunctie'", new MapHandler());
            String fotoDir = settings.get("value").toString();
            
            //get all photos that are older than INTERVAL
            List<Map<String, Object>> rs = DB.qr().query("select * from wfs.\"FotoFunctie\" where date <=  CURRENT_DATE + INTERVAL '-" + INTERVAL + " day'", new MapListHandler());
            
            //Loop through photo's and delete it.
            for (Map<String, Object> photo : rs) {
                String fileName = photo.get("filename").toString();
                System.out.println(fileName);
                File file = new File(fotoDir + fileName);
                if (file.delete()) {
                    LOG.debug(fileName + " deleted");
                } else {
                    LOG.debug(fileName + " bestaat niet");
                }
            }
            
            //finally delete Photo's from DB
            DB.qr().update("delete from wfs.\"FotoFunctie\" where datum <=  CURRENT_DATE + INTERVAL '-" + INTERVAL + " day'");

        } catch (NamingException ex) {
            LOG.error(ex);
        } catch (SQLException ex) {
            LOG.error(ex);
        }
    }

}
