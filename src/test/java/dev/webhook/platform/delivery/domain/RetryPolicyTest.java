package dev.webhook.platform.delivery.domain;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;


class RetryPolicyTest {

    @Test
    void firstFailure_schedulesRetryAfterTenSeconds() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        RetryPolicy retryPolicy = new RetryPolicy();

        RetryDecision decision = retryPolicy.decideAfterFailure(1,
                new FailureResult(null, "timeout"),
                now);

        assertThat(decision.shouldRetry()).isTrue();
        assertThat(decision.nextAttemptAt()).isEqualTo(now.plusSeconds(10));
    }

    @Test
    void secondFailure_schedulesRetryAfterThirtySeconds() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        RetryPolicy retryPolicy = new RetryPolicy();

        RetryDecision decision = retryPolicy.decideAfterFailure(2,
                new FailureResult(null, "timeoutr"),
                now);

        assertThat(decision.shouldRetry()).isTrue();
        assertThat(decision.nextAttemptAt()).isEqualTo(now.plusSeconds(30));
    }

    @Test
    void thirdFailure_isTerminal() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        RetryPolicy retryPolicy = new RetryPolicy();

        RetryDecision decision = retryPolicy.decideAfterFailure(3,
                new FailureResult(null, "timeout"),
                now);

        assertThat(decision.shouldRetry()).isFalse();
        assertNull(decision.nextAttemptAt());
    }
    @Test
    void http404Failure_isTerminalImmediately() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        RetryPolicy retryPolicy = new RetryPolicy();

        RetryDecision decision = retryPolicy.decideAfterFailure(
                1,
                new FailureResult(404, "not found"),
                now);

        assertThat(decision.shouldRetry()).isFalse();
        assertThat(decision.nextAttemptAt()).isNull();
    }
    @Test
    void http429Failure_schedulesSlowerRetry() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        RetryPolicy retryPolicy = new RetryPolicy();

        RetryDecision decision = retryPolicy.decideAfterFailure(
                1,
                new FailureResult(429, "too many requests"),
                now);

        assertThat(decision.shouldRetry()).isTrue();
        assertThat(decision.nextAttemptAt()).isEqualTo(now.plusSeconds(30));
    }
    @Test
    void http500Failure_schedulesRetry() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        RetryPolicy retryPolicy = new RetryPolicy();

        RetryDecision decision = retryPolicy.decideAfterFailure(
                1,
                new FailureResult(500, "internal server error"),
                now);

        assertThat(decision.shouldRetry()).isTrue();
        assertThat(decision.nextAttemptAt()).isEqualTo(now.plusSeconds(10));
    }
}
