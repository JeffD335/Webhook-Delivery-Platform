package dev.webhook.platform.endpoint.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = EndpointUrlValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface ValidEndpointUrl {

    String message() default "must be an absolute HTTP or HTTPS URL";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}