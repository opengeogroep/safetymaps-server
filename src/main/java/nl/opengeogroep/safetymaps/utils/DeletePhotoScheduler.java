/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.utils;

import java.util.Properties;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

/**
 *
 * @author martijn
 */
public class DeletePhotoScheduler implements ServletContextListener {

    private static final Log LOG = LogFactory.getLog(DeletePhotoScheduler.class);

    private static Scheduler scheduler = null;

    private static ServletContext context;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        this.context = sce.getServletContext();

        try {
            // get Scheduler (singleton)
            scheduler = getInstance();
            
            //Make job that executes the DeletePhotoJob
            JobDetail job = JobBuilder.newJob(DeletePhotoJob.class)
                    .withIdentity("deletePhoto")
                    .withDescription("deletePhotos after x days")
                    .build();
            
            //Make a trigger for the job, every sunday at 00:00:00am
            CronExpression c = new CronExpression("0 0 0 ? * SUN *");
            CronScheduleBuilder cronSchedule = CronScheduleBuilder.cronSchedule(c);
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("deletePhoto trigger")
                    .startNow()
                    .withSchedule(cronSchedule)
                    .build();
            
            //schedule the job
            scheduler.scheduleJob(job, trigger);
            LOG.debug("Job deletePhoto created");
        } catch (Exception e) {

        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (scheduler != null) {
            try {
                scheduler.shutdown(true);
                LOG.debug("scheduler stopped");
            } catch (SchedulerException ex) {
                LOG.error("Cannot shutdown quartz scheduler. ", ex);
            }
        }
    }

    public static Scheduler getInstance() throws SchedulerException {

        if (scheduler == null) {
            try {
                Properties props = new Properties();
                props.put("org.quartz.scheduler.instanceName", "DeletePhotoScheduler");
                props.put("org.quartz.threadPool.threadCount", "1");
                props.put("org.quartz.scheduler.interruptJobsOnShutdownWithWait", "true");
                // Job store for monitoring does not need to be persistent
                props.put("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
                scheduler = new StdSchedulerFactory(props).getScheduler();
                scheduler.start();
                LOG.debug("scheduler created and started");
            } catch (SchedulerException ex) {
                LOG.error("Cannot create scheduler. ", ex);
            }
        }

        return scheduler;

    }

}
