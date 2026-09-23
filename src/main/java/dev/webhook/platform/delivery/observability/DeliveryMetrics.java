package dev.webhook.platform.delivery.observability;

import dev.webhook.platform.delivery.sender.SendResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class DeliveryMetrics {
    public record TimedSend(SendResult result, String resultCategory, long durationMs) {}

    private static final String SEND_METRIC = "webhook.delivery.send";
    private static final String TRANSITION_METRIC = "webhook.delivery.transitions";
    private static final String WORKER_ERROR_METRIC = "webhook.delivery.worker.errors";

    private final MeterRegistry registry;
    private final Timer success;
    private final Timer httpError;
    private final Timer noResponse;
    private final Timer exception;
    private final Counter transitionSucceeded;
    private final Counter transitionRetryScheduled;
    private final Counter transitionFailed;
    private final Counter workerErrors;

    public DeliveryMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
        this.success = registerSendTimer("success");
        this.httpError = registerSendTimer("http_error");
        this.noResponse = registerSendTimer("no_response");
        this.exception = registerSendTimer("exception");
        this.transitionSucceeded = registerTransitionCounter("succeeded");
        this.transitionRetryScheduled = registerTransitionCounter("retry_scheduled");
        this.transitionFailed = registerTransitionCounter("failed");
        this.workerErrors = Counter.builder(WORKER_ERROR_METRIC)
                .description("Unexpected runtime errors while processing webhook deliveries")
                .register(registry);
    }

    public TimedSend timeSend(Supplier<SendResult> sendAction) {
        Objects.requireNonNull(sendAction);
        Timer.Sample sample = Timer.start(registry);

        SendResult result;
        try {
            result = Objects.requireNonNull(
                    sendAction.get(),
                    "WebhookSender returned null"
            );
        } catch (RuntimeException failure) {
            sample.stop(exception);
            throw failure;
        }

        Timer timer = timerFor(result);
        long durationNanos = sample.stop(timer);
        return new TimedSend(
                result,
                timer.getId().getTag("result"),
                TimeUnit.NANOSECONDS.toMillis(durationNanos));
    }

    public void recordTransitionSucceeded() {
        transitionSucceeded.increment();
    }

    public void recordTransitionRetryScheduled() {
        transitionRetryScheduled.increment();
    }

    public void recordTransitionFailed() {
        transitionFailed.increment();
    }

    public void recordWorkerError() {
        workerErrors.increment();
    }

    private Timer timerFor(SendResult result) {
        if (result.succeeded()) {
            return success;
        }
        return result.httpStatus() == null ? noResponse : httpError;
    }

    private Timer registerSendTimer(String result) {
        return Timer.builder(SEND_METRIC)
                .description("Time spent sending webhook deliveries")
                .tag("result", result)
                .register(registry);
    }

    private Counter registerTransitionCounter(String outcome) {
        return Counter.builder(TRANSITION_METRIC)
                .description("Persisted webhook delivery processing outcomes")
                .tag("outcome", outcome)
                .register(registry);
    }
}
