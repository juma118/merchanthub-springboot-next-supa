package com.merchanthub.scheduler;

import com.merchanthub.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

@Configuration
public class SchedulingConfig implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

    private final AppProperties props;
    private final SyncScheduler scheduler;

    public SchedulingConfig(AppProperties props, SyncScheduler scheduler) {
        this.props = props;
        this.scheduler = scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        long interval = props.getSyncIntervalMs();
        if (interval <= 0) {
            log.info("Scheduled pull-sync disabled (merchanthub.sync-interval-ms={})", interval);
            return;
        }
        log.info("Scheduling pull-sync every {} ms", interval);
        registrar.addFixedDelayTask(scheduler::runAllTenants, Duration.ofMillis(interval));
    }
}
