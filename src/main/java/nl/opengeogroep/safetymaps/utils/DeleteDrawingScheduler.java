package nl.opengeogroep.safetymaps.utils;

import java.util.Properties;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import nl.opengeogroep.safetymaps.server.db.DB;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

/**
 *
 * @author matthijsln
 */
public class DeleteDrawingScheduler implements ServletContextListener {

    private static final Log LOG = LogFactory.getLog(DeleteDrawingScheduler.class);

    private static Scheduler scheduler = null;

    private ServletContext context;

    public static class DeleteDrawingJob implements Job {
        private final int INTERVAL = 7;

        @Override
        public void execute(JobExecutionContext jec) throws JobExecutionException {
            try {
                int deleted = DB.qr().update("delete from safetymaps.drawing where last_modified <= CURRENT_DATE + INTERVAL '-" + INTERVAL + " day'");
                LOG.info("Deleted " + deleted + " drawings last modified more than " + INTERVAL + " days ago");
            } catch (Exception ex) {
                LOG.error(ex);
            }
        }
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        this.context = sce.getServletContext();

        try {
            scheduler = getInstance();

            JobDetail job = JobBuilder.newJob(DeleteDrawingJob.class)
                    .withIdentity("deleteDrawing")
                    .withDescription("Delete drawings after x days")
                    .build();

            // Make a trigger for the job, every sunday at 00:10:00am
            CronExpression c = new CronExpression("0 10 0 ? * SUN *");
            CronScheduleBuilder cronSchedule = CronScheduleBuilder.cronSchedule(c);

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("deleteDrawing trigger")
                    .startNow()
                    .withSchedule(cronSchedule)
                    .build();

            scheduler.scheduleJob(job, trigger);
            LOG.info("Job deleteDrawing created: " + c.getExpressionSummary() + ", " + c.getFinalFireTime());
        } catch (Exception e) {
            LOG.error("Error creating job", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if(scheduler != null) {
            try {
                scheduler.shutdown(true);
                LOG.debug("Scheduler stopped");
            } catch (SchedulerException ex) {
                LOG.error("Cannot shutdown quartz scheduler", ex);
            }
        }
    }

    public static Scheduler getInstance() throws SchedulerException {
        if(scheduler == null) {
            try {
                Properties props = new Properties();
                props.put("org.quartz.scheduler.instanceName", "DeleteDrawingScheduler");
                props.put("org.quartz.threadPool.threadCount", "1");
                props.put("org.quartz.scheduler.interruptJobsOnShutdownWithWait", "true");
                // Job store does not need to be persistent
                props.put("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
                scheduler = new StdSchedulerFactory(props).getScheduler();
                scheduler.start();
                LOG.info("Scheduler created and started");
            } catch (SchedulerException ex) {
                LOG.error("Cannot create scheduler", ex);
            }
        }

        return scheduler;
    }
}
