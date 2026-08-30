package dev.webhook.platform.endpoint.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;

public class EndpointUrlValidator implements ConstraintValidator<ValidEndpointUrl, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }

        if (!value.equals(value.strip())) {
            return false;
        }

        if (value.length() > 2048) {
            return true;
        }

        try {
            URI uri = URI.create(value);

            if (!uri.isAbsolute()) {
                return false;
            }

            String scheme = uri.getScheme();
            if (scheme == null ||
                    (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return false;
            }

            return uri.getHost() != null && !uri.getHost().isBlank();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}