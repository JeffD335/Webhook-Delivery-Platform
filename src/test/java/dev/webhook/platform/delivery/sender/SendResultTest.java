package dev.webhook.platform.delivery.sender;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SendResultTest {

    @Test
    void successResult_carriesHttpStatus() {
        SendResult result = SendResult.success(204);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(204);
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void failureResult_canCarryHttpStatusAndErrorMessage() {
        SendResult result = SendResult.httpFailure(503, "service unavailable");

        assertThat(result.succeeded()).isFalse();
        assertThat(result.httpStatus()).isEqualTo(503);
        assertThat(result.errorMessage()).isEqualTo("service unavailable");
    }

    @Test
    void timeoutResult_hasNoHttpStatusAndHasErrorMessage() {
        SendResult result = SendResult.timeOut("read timed out");

        assertThat(result.succeeded()).isFalse();
        assertThat(result.httpStatus()).isNull();
        assertThat(result.errorMessage()).isEqualTo("read timed out");
    }
}
