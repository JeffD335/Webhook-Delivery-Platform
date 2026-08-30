package dev.webhook.platform.delivery.domain;

import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicy {
    private static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(10);
    private static final Duration SECOND_RETRY_DELAY = Duration.ofSeconds(30);

    public RetryPolicy() {
    }

    public RetryDecision decideAfterFailure(int failedAttemptNumber, FailureResult failure, Instant now) {
        if (failedAttemptNumber < 1) {
            throw new IllegalArgumentException("failedAttemptNumber must be positive");
        }

        if (isPermanentFailure(failure)) {
            return new RetryDecision(false, null);
        }

        if (failedAttemptNumber < 3) {
            return new RetryDecision(true, now.plus(delayFor(failure, failedAttemptNumber)));
        }

        return new RetryDecision(false, null);
    }

    private boolean isPermanentFailure(FailureResult failure) {
        Integer httpStatus = failure.httpStatus();

        return httpStatus != null
                && (httpStatus == 400
                || httpStatus == 401
                || httpStatus == 403
                || httpStatus == 404);
    }
    private Duration delayFor(FailureResult failure, int failedAttemptNumber) {
        if (Integer.valueOf(429).equals(failure.httpStatus())) {
            if (failedAttemptNumber == 1) {
                return Duration.ofSeconds(30);
            }
            return Duration.ofMinutes(2);
        }

        if (failedAttemptNumber == 1) {
            return FIRST_RETRY_DELAY;
        }

        return SECOND_RETRY_DELAY;
    }
}
