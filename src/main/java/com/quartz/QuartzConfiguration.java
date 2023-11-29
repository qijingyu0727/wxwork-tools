package com.quartz;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Configuration
public class QuartzConfiguration {
    @Value("${spring.datasource.driver-class-name}")
    private String driver;

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String user;

    @Value("${spring.datasource.password}")
    private String password;

    public Properties quartzProperties() {
        Properties prop = new Properties();
        prop.put("org.quartz.scheduler.instanceName", "WxworkSchedule");
        prop.put("org.quartz.scheduler.instanceId", System.currentTimeMillis());
        prop.put("org.quartz.scheduler.skipUpdateCheck", "true");
        prop.put("org.quartz.scheduler.jmx.export", "true");

        prop.put("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        prop.put("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
        prop.put("org.quartz.jobStore.tablePrefix", "QRTZ_");
        prop.put("org.quartz.jobStore.isClustered", "true");

        prop.put("org.quartz.jobStore.clusterCheckinInterval", "30000");
        prop.put("org.quartz.jobStore.maxMisfiresToHandleAtATime", "1");
        prop.put("org.quartz.jobStore.misfireThreshold", "120000");
        prop.put("org.quartz.jobStore.txIsolationLevelSerializable", "true");

        prop.put("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        prop.put("org.quartz.threadPool.threadCount", "50");
        prop.put("org.quartz.threadPool.threadPriority", "5");
        prop.put("org.quartz.threadPool.threadsInheritContextClassLoaderOfInitializingThread", "true");


        prop.put("org.quartz.plugin.triggHistory.class", "org.quartz.plugins.history.LoggingJobHistoryPlugin");
        prop.put("org.quartz.plugin.shutdownhook.class", "org.quartz.plugins.management.ShutdownHookPlugin");
        prop.put("org.quartz.plugin.shutdownhook.cleanShutdown", "true");


        prop.put("org.quartz.jobStore.dataSource", "test");

        return prop;
    }

    @Bean
    public ComboPooledDataSource createDataSource() throws PropertyVetoException {
        ComboPooledDataSource dataSource = new ComboPooledDataSource();
        dataSource.setDriverClass(driver);
        dataSource.setJdbcUrl(url);
        dataSource.setUser(user);
        dataSource.setPassword(password);
        return dataSource;
    }

    @Bean("WxworkSchedule")
    public SchedulerFactoryBean schedulerFactoryBean(@Qualifier("archiveMsgTrigger") Trigger archiveMsgTrigger) throws IOException, PropertyVetoException{
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setOverwriteExistingJobs(true);
        factory.setStartupDelay(10);
        factory.setQuartzProperties(quartzProperties());
        factory.setAutoStartup(true);
        factory.setApplicationContextSchedulerContextKey("applicationContext");
        factory.setDataSource(createDataSource());
        //注册触发器
        factory.setTriggers(archiveMsgTrigger);

        return factory;
    }

    /**
     * 创建job工厂
     * @param jobClass
     * @param groupName
     * @param targetObject
     * @return
     */
    private static JobDetailFactoryBean createJobDetail(Class<?> jobClass, String groupName, String targetObject , String targetMethpd) {
        JobDetailFactoryBean factoryBean = new JobDetailFactoryBean();
        factoryBean.setJobClass(jobClass);
        factoryBean.setDurability(true);
        factoryBean.setRequestsRecovery(true);
        factoryBean.setGroup(groupName);
        Map<String, String> map = new HashMap<>();
        map.put("targetObject", targetObject);
        map.put("targetMethod", targetMethpd);
        factoryBean.setJobDataAsMap(map);
        return factoryBean;
    }

    /**
     * 创建触发器工厂
     * @param jobDetail
     * @param cronExpression
     * @return
     */
    private static CronTriggerFactoryBean dialogStatusTrigger(JobDetail jobDetail, String cronExpression) {
        CronTriggerFactoryBean factoryBean = new CronTriggerFactoryBean();
        factoryBean.setJobDetail(jobDetail);
        factoryBean.setCronExpression (cronExpression);
        return factoryBean;
    }

    /****************************************************************************************************
     * 新增定时任务时 ， 需新增 JobDetailFactoryBean 及 CronTriggerFactoryBean
     * 并在schedulerFactoryBean 的参数里加上新的 trigger  ， 并在该方法的         factory.setTriggers(cronJobTrigger); 里加上trigger
     ****************************************************************************************************/

    /**
     * 解析巡检日志 jobDetail
     * @return
     */
    @Bean("archiveMsgJobDetail")
    public JobDetailFactoryBean archiveMsgJobDetail() {
        return createJobDetail(InvokingJobDetailDetailFactory.class, null, "archiveMsgJob" , "start");
    }

    /**
     * 解析巡检日志 trigger
     * @param jobDetail
     * @return
     */
    @Bean(name = "archiveMsgTrigger")
    public CronTriggerFactoryBean archiveMsgTrigger(@Qualifier("archiveMsgJobDetail") JobDetail jobDetail) {
        return dialogStatusTrigger(jobDetail, "0 */10 * * * ?");
    }
}

