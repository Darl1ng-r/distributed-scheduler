package com.orchestrator.common.validation;

import com.orchestrator.common.util.UrlSecurityValidator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class WebhookUrlConstraintValidator implements ConstraintValidator<ValidWebhookUrl, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // Use @NotBlank separately if required
        }
        return UrlSecurityValidator.isValidWebhookUrl(value);
    }
}
