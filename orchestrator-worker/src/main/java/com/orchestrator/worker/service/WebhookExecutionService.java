package com.orchestrator.worker.service;

import com.orchestrator.common.dto.TaskMessagePayload;
import com.orchestrator.common.util.HmacSigner;
import com.orchestrator.common.util.UrlSecurityValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;

@Slf4j
@Service
public class WebhookExecutionService {

    private final RestClient restClient;
    private final DomainRateLimiterService rateLimiterService;
    private final String signingSecret;

    public WebhookExecutionService(@Value("${worker.connect-timeout-ms:5000}") int connectTimeout,
                                   @Value("${worker.read-timeout-ms:10000}") int readTimeout,
                                   @Value("${worker.webhook-signing-secret:super-secret-hmac-key-change-in-prod}") String signingSecret,
                                   DomainRateLimiterService rateLimiterService) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.rateLimiterService = rateLimiterService;
        this.signingSecret = signingSecret;
    }

    public int executeWebhook(TaskMessagePayload payload) {
        if (!UrlSecurityValidator.isValidWebhookUrl(payload.getWebhookUrl())) {
            throw new IllegalArgumentException("Security violation: Webhook URL '" + payload.getWebhookUrl() + "' failed SSRF validation");
        }

        boolean acquired = false;
        try {
            acquired = rateLimiterService.tryAcquire(payload.getWebhookUrl(), 30000);
            if (!acquired) {
                throw new IllegalStateException("Failed to acquire domain concurrency permit within timeout for " + payload.getWebhookUrl());
            }

            String timestamp = OffsetDateTime.now().toString();
            String signaturePayload = payload.getExecutionId() + ":" + timestamp + ":" + payload.getTaskScheduleId();
            String signature = HmacSigner.calculateSignature(signaturePayload, signingSecret);

            log.info("Executing webhook POST to '{}' for execution ID {}", payload.getWebhookUrl(), payload.getExecutionId());

            RestClient.RequestBodySpec requestSpec = restClient.post()
                    .uri(payload.getWebhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Orchestrator-Execution-ID", payload.getExecutionId())
                    .header("X-Orchestrator-Timestamp", timestamp)
                    .header("X-Orchestrator-Signature", signature);

            if (payload.getHeaders() != null) {
                payload.getHeaders().forEach(requestSpec::header);
            }

            ResponseEntity<String> response = requestSpec
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);

            log.info("Webhook POST to '{}' responded with HTTP {}", payload.getWebhookUrl(), response.getStatusCode().value());
            return response.getStatusCode().value();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while acquiring domain rate limit permit", e);
        } finally {
            if (acquired) {
                rateLimiterService.release(payload.getWebhookUrl());
            }
        }
    }
}
