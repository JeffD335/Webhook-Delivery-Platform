package dev.webhook.platform.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        String code,
        String message,
        Map<String, String> fieldErrors) {

    public ApiError {
        fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }

    public ApiError(String code, String message) {
        this(code, message, Map.of());
    }
}
