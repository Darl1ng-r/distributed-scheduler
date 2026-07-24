package com.orchestrator.worker.service;

import com.orchestrator.common.dto.TaskMessagePayload;
import com.orchestrator.common.enums.ExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskResultHandlerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private TaskResultHandler resultHandler;

    @BeforeEach
    void setUp() {
        resultHandler = new TaskResultHandler(jdbcTemplate, rabbitTemplate);
    }

    @Test
    @DisplayName("Should update execution record to SUCCESS on webhook success")
    void shouldHandleSuccess() {
        TaskMessagePayload payload = TaskMessagePayload.builder()
                .executionId(UUID.randomUUID().toString())
                .currentAttempt(1)
                .build();

        resultHandler.handleSuccess(payload, 200);

        verify(jdbcTemplate, times(1)).update(
                any(String.class),
                eq(ExecutionStatus.SUCCESS.name()),
                eq(200),
                any(),
                eq(1),
                eq(payload.getExecutionId())
        );
    }

    @Test
    @DisplayName("Should schedule retry to RabbitMQ retry exchange when attempt < maxRetries")
    void shouldScheduleRetryWhenAttemptLessThanMax() {
        TaskMessagePayload payload = TaskMessagePayload.builder()
                .executionId(UUID.randomUUID().toString())
                .currentAttempt(1)
                .maxRetries(3)
                .initialIntervalSec(5)
                .backoffMultiplier(2.0)
                .build();

        Exception cause = new RuntimeException("504 Gateway Timeout");

        resultHandler.handleFailure(payload, cause, 504);

        verify(jdbcTemplate, times(1)).update(
                any(String.class),
                eq(ExecutionStatus.RETRYING.name()),
                eq("504 Gateway Timeout"),
                eq(504),
                eq(1),
                eq(payload.getExecutionId())
        );

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(TaskResultHandler.RETRY_EXCHANGE),
                eq(TaskResultHandler.RETRY_ROUTING_KEY),
                eq(payload),
                any(MessagePostProcessor.class)
        );
    }

    @Test
    @DisplayName("Should mark status as FAILED permanently when max retries exceeded")
    void shouldMarkFailedWhenMaxRetriesExceeded() {
        TaskMessagePayload payload = TaskMessagePayload.builder()
                .executionId(UUID.randomUUID().toString())
                .currentAttempt(3)
                .maxRetries(3)
                .build();

        Exception cause = new RuntimeException("404 Not Found");

        resultHandler.handleFailure(payload, cause, 404);

        verify(jdbcTemplate, times(1)).update(
                any(String.class),
                eq(ExecutionStatus.FAILED.name()),
                eq("404 Not Found"),
                eq(404),
                any(),
                eq(3),
                eq(payload.getExecutionId())
        );

        verifyNoInteractions(rabbitTemplate);
    }
}
