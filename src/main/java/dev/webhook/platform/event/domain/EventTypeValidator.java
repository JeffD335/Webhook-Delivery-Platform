package dev.webhook.platform.event.domain;

/**
 * Pure business rule for event type identifiers.
 *
 * Implement this without Spring, repositories, or HTTP concerns.
 */
public final class EventTypeValidator {

    private static final int MAX_LENGTH = 100;
    private static final String SEGMENT_PATTERN = "[a-z][a-z0-9_-]*";
    private static final String EVENT_TYPE_PATTERN = SEGMENT_PATTERN + "\\." + SEGMENT_PATTERN;

    private EventTypeValidator() {
    }

    public static boolean isValid(String type) {
        if (type == null || type.isBlank() || type.length() > MAX_LENGTH) {
            return false;
        }

        return type.matches(EVENT_TYPE_PATTERN);
    }
}
