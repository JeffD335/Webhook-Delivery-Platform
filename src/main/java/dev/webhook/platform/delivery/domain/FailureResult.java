package dev.webhook.platform.delivery.domain;

public record FailureResult(
        Integer httpStatus,
        String errorMessage) {
}