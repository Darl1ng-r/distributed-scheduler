package com.orchestrator.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = WebhookUrlConstraintValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidWebhookUrl {
    String message() default "Webhook URL must be a valid public HTTP/HTTPS URL and cannot target local or private IP addresses (SSRF protection)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
