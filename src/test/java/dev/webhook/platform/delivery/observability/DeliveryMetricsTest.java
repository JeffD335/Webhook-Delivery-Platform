package dev.webhook.platform.delivery.observability;

import dev.webhook.platform.delivery.sender.SendResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryMetricsTest {

    @Test
    void registersEachSendResultAtZero() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new DeliveryMetrics(registry);

        for (String result : List.of("success", "http_error", "no_response", "exception")) {
            assertThat(timer(registry, result).count()).isZero();
        }
    }

    @Test
    void registersEachTransitionAndWorkerErrorAtZero() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new DeliveryMetrics(registry);

        for (String outcome : List.of("succeeded", "retry_scheduled", "failed")) {
            assertThat(transitionCounter(registry, outcome).count()).isZero();
        }
        assertThat(workerErrorCounter(registry).count()).isZero();
    }

    @Test
    void recordTransitionSucceeded_incrementsOnlySucceededTransition() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DeliveryMetrics metrics = new DeliveryMetrics(registry);

        metrics.recordTransitionSucceeded();

        assertThat(transitionCounter(registry, "succeeded").count()).isEqualTo(1);
        assertThat(transitionCounter(registry, "retry_scheduled").count()).isZero();
        assertThat(transitionCounter(registry, "failed").count()).isZero();
    }

    @Test
    void recordTransitionRetryScheduled_incrementsOnlyRetryScheduledTransition() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DeliveryMetrics metrics = new DeliveryMetrics(registry);

        metrics.recordTransitionRetryScheduled();

        assertThat(transitionCounter(registry, "succeeded").count()).isZero();
        assertThat(transitionCounter(registry, "retry_scheduled").count()).isEqualTo(1);
        assertThat(transitionCounter(registry, "failed").count()).isZero();
    }

    @Test
    void recordTransitionFailed_incrementsOnlyFailedTransition() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DeliveryMetrics metrics = new DeliveryMetrics(registry);

        metrics.recordTransitionFailed();

        assertThat(transitionCounter(registry, "succeeded").count()).isZero();
        assertThat(transitionCounter(registry, "retry_scheduled").count()).isZero();
        assertThat(transitionCounter(registry, "failed").count()).isEqualTo(1);
    }

    @Test
    void recordWorkerError_incrementsWorkerErrorCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DeliveryMetrics metrics = new DeliveryMetrics(registry);

        metrics.recordWorkerError();

        assertThat(workerErrorCounter(registry).count()).isEqualTo(1);
        for (String outcome : List.of("succeeded", "retry_scheduled", "failed")) {
            assertThat(transitionCounter(registry, outcome).count()).isZero();
        }
    }

    @Test
    void timeSend_whenSendSucceeds_recordsCountAndDuration() {
        MockClock clock = new MockClock();
        SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
        DeliveryMetrics metrics = new DeliveryMetrics(registry);

        DeliveryMetrics.TimedSend timedSend = metrics.timeSend(() -> {
            clock.add(Duration.ofMillis(75));
            return SendResult.success(204);
        });

        Timer success = timer(registry, "success");

        assertThat(timedSend.result()).isEqualTo(SendResult.success(204));
        assertThat(timedSend.resultCategory()).isEqualTo("success");
        assertThat(timedSend.durationMs()).isEqualTo(75);
        assertThat(success.count()).isEqualTo(1);
        assertThat(success.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(75);
    }

    @Test
    void timeSend_whenHttpRequestFails_recordsHttpError() {
        MockClock clock = new MockClock();
        SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
        DeliveryMetrics metrics = new DeliveryMetrics(registry);

        DeliveryMetrics.TimedSend timedSend = metrics.timeSend(() -> {
            clock.add(Duration.ofMillis(30));
            return SendResult.httpFailure(503, "service unavailable");
        });

        assertThat(timedSend.result()).isEqualTo(SendResult.httpFailure(503, "service unavailable"));
        assertThat(timedSend.resultCategory()).isEqualTo("http_error");
        assertThat(timedSend.durationMs()).isEqualTo(30);
        assertThat(timer(registry, "http_error").count()).isEqualTo(1);
        assertThat(timer(registry, "http_error").totalTime(TimeUnit.MILLISECONDS)).isEqualTo(30);
        assertThat(timer(registry, "success").count()).isZero();
        assertThat(timer(registry, "no_response").count()).isZero();
        assertThat(timer(registry, "exception").count()).isZero();
    }

    @Test
    void timeSend_whenNoHttpResponseIsReceived_recordsNoResponse() {
        MockClock clock = new MockClock();
        SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
        DeliveryMetrics metrics = new DeliveryMetrics(registry);

        DeliveryMetrics.TimedSend timedSend = metrics.timeSend(() -> {
            clock.add(Duration.ofMillis(50));
            return SendResult.timeOut("read timed out");
        });

        assertThat(timedSend.result()).isEqualTo(SendResult.timeOut("read timed out"));
        assertThat(timedSend.resultCategory()).isEqualTo("no_response");
        assertThat(timedSend.durationMs()).isEqualTo(50);
        assertThat(timer(registry, "no_response").count()).isEqualTo(1);
        assertThat(timer(registry, "no_response").totalTime(TimeUnit.MILLISECONDS)).isEqualTo(50);
        assertThat(timer(registry, "success").count()).isZero();
        assertThat(timer(registry, "http_error").count()).isZero();
        assertThat(timer(registry, "exception").count()).isZero();
    }

    @Test
    void timeSend_whenSenderThrows_recordsExceptionAndRethrowsSameException() {
        MockClock clock = new MockClock();
        SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
        DeliveryMetrics metrics = new DeliveryMetrics(registry);
        IllegalStateException failure = new IllegalStateException("sender failed");

        assertThatThrownBy(() -> metrics.timeSend(() -> {
            clock.add(Duration.ofMillis(40));
            throw failure;
        })).isSameAs(failure);

        assertThat(timer(registry, "exception").count()).isEqualTo(1);
        assertThat(timer(registry, "exception").totalTime(TimeUnit.MILLISECONDS)).isEqualTo(40);
        assertThat(timer(registry, "success").count()).isZero();
        assertThat(timer(registry, "http_error").count()).isZero();
        assertThat(timer(registry, "no_response").count()).isZero();
    }

    private Timer timer(SimpleMeterRegistry registry, String result) {
        return registry.get("webhook.delivery.send")
                .tag("result", result)
                .timer();
    }

    private Counter transitionCounter(SimpleMeterRegistry registry, String outcome) {
        return registry.get("webhook.delivery.transitions")
                .tag("outcome", outcome)
                .counter();
    }

    private Counter workerErrorCounter(SimpleMeterRegistry registry) {
        return registry.get("webhook.delivery.worker.errors")
                .counter();
    }
}
