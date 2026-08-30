package dev.webhook.platform.delivery.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class DeliveryRuntimeConfigLogger {
    private static final Logger log = LoggerFactory.getLogger(DeliveryRuntimeConfigLogger.class);

    private final boolean schedulerEnabled;
    private final long schedulerFixedDelayMs;
    private final int workerCount;
    private final String workerId;
    private final boolean manualRunnerEnabled;
    DeliveryRuntimeConfigLogger(
            @Value("${webhook.delivery.scheduler.enabled:true}") boolean schedulerEnabled,
            @Value("${webhook.delivery.scheduler.fixed-delay-ms:1000}") long schedulerFixedDelayMs,
            @Value("${webhook.delivery.worker-count:3}") int workerCount,
            @Value("${webhook.delivery.worker-id:worker-1}") String workerId,
            @Value("${webhook.delivery.manual-runner.enabled:true}") boolean manualRunnerEnabled) {
        this.schedulerEnabled = schedulerEnabled;
        this.schedulerFixedDelayMs = schedulerFixedDelayMs;
        this.workerCount = workerCount;
        this.workerId = workerId;
        this.manualRunnerEnabled = manualRunnerEnabled;
    }
    @EventListener(ApplicationReadyEvent.class)
    void logRuntimeConfig() {
        log.info(
                "delivery runtime config schedulerEnabled={} schedulerFixedDelayMs={} workerCount={} workerId={} manualRunnerEnabled={}",
                schedulerEnabled,
                schedulerFixedDelayMs,
                workerCount,
                workerId,
                manualRunnerEnabled);
    }
}
