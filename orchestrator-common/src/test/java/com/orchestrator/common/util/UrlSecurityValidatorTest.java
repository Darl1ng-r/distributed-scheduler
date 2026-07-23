package com.orchestrator.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class UrlSecurityValidatorTest {

    @Test
    @DisplayName("Should accept valid public HTTPS URL")
    void shouldAcceptValidPublicUrl() {
        assertTrue(UrlSecurityValidator.isValidWebhookUrl("https://api.github.com/hooks"));
        assertTrue(UrlSecurityValidator.isValidWebhookUrl("http://example.com/webhook"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:8080/hook",
            "http://localhost:5432",
            "http://169.254.169.254/latest/meta-data/",
            "http://10.0.0.1/admin",
            "http://172.16.0.1/status",
            "http://192.168.1.1/config",
            "http://0.0.0.0:8000"
    })
    @DisplayName("Should reject internal, loopback, link-local, and cloud IMDS URLs (SSRF Protection)")
    void shouldRejectRestrictedUrls(String url) {
        assertFalse(UrlSecurityValidator.isValidWebhookUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com/file",
            "gopher://example.com",
            "file:///etc/passwd",
            "not-a-url"
    })
    @DisplayName("Should reject invalid schemes or invalid URLs")
    void shouldRejectInvalidSchemes(String url) {
        assertFalse(UrlSecurityValidator.isValidWebhookUrl(url));
    }
}
