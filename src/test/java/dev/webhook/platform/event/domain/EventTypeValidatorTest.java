package dev.webhook.platform.event.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Event type business rule.
 *
 * Run only this class with:
 * mvn -Dtest=EventTypeValidatorTest test
 */
class EventTypeValidatorTest {

    @Test
    void validDottedLowercaseTypes_returnTrue() {
        assertThat(EventTypeValidator.isValid("order.created")).isTrue();
        assertThat(EventTypeValidator.isValid("invoice.paid")).isTrue();
        assertThat(EventTypeValidator.isValid("user.signup")).isTrue();
        assertThat(EventTypeValidator.isValid("inventory-item.updated")).isTrue();
    }

    @Test
    void blankType_returnsFalse() {
        assertThat(EventTypeValidator.isValid(null)).isFalse();
        assertThat(EventTypeValidator.isValid("")).isFalse();
        assertThat(EventTypeValidator.isValid("   ")).isFalse();
    }

    @Test
    void uppercaseType_returnsFalse() {
        assertThat(EventTypeValidator.isValid("Order.Created")).isFalse();
    }

    @Test
    void missingDot_returnsFalse() {
        assertThat(EventTypeValidator.isValid("order")).isFalse();
    }

    @Test
    void extraSegment_returnsFalse() {
        assertThat(EventTypeValidator.isValid("order.created.now")).isFalse();
    }

    @Test
    void spaceInType_returnsFalse() {
        assertThat(EventTypeValidator.isValid("order created")).isFalse();
    }

    @Test
    void overLengthType_returnsFalse() {
        assertThat(EventTypeValidator.isValid("a".repeat(101) + ".created")).isFalse();
    }
}
