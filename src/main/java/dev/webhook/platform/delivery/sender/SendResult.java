package dev.webhook.platform.delivery.sender;

/**
 * Result returned by the sender abstraction after one send try.
 */
public record SendResult(
        boolean succeeded,
        Integer httpStatus,
        String errorMessage) {

    public static SendResult success() {
        return new SendResult(true, 200, null);
    }

    public static SendResult failure(String errorMessage) {
        return new SendResult(false, 500, errorMessage);
    }

    public static SendResult timeOut(String errorMessage) {
        return new SendResult(false, null, errorMessage);
    }
    public static SendResult success(int httpStatus) {
        return new SendResult(true, httpStatus, null);
    }

    public static SendResult httpFailure(int httpStatus, String errorMessage) {
        return new SendResult(false, httpStatus, errorMessage);
    }
}
