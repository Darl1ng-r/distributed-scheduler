package com.orchestrator.worker.service;

import com.orchestrator.common.dto.TaskMessagePayload;
import com.orchestrator.common.enums.ExecutionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskResultHandler {

    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;

    public static final String RETRY_EXCHANGE = "task.retry.exchange";
    public static final String RETRY_ROUTING_KEY = "task.retry";

    public void handleSuccess(TaskMessagePayload payload, int statusCode) {
        log.info("Task execution SUCCESS [Execution ID: {}]", payload.getExecutionId());
        String sql = "UPDATE task_executions SET status = ?, response_status = ?, completed_at = ?, attempt = ? WHERE id = ?";
        jdbcTemplate.update(sql, ExecutionStatus.SUCCESS.name(), statusCode, OffsetDateTime.now(), payload.getCurrentAttempt(), payload.getExecutionId());
    }

    public void handleFailure(TaskMessagePayload payload, Throwable cause, Integer statusCode) {
        int attempt = payload.getCurrentAttempt();
        int maxRetries = payload.getMaxRetries() > 0 ? payload.getMaxRetries() : 3;

        if (attempt < maxRetries) {
            int nextAttempt = attempt + 1;
            double multiplier = payload.getBackoffMultiplier() > 0 ? payload.getBackoffMultiplier() : 2.0;
            int initialInterval = payload.getInitialIntervalSec() > 0 ? payload.getInitialIntervalSec() : 5;
            long delaySec = (long) (initialInterval * Math.pow(multiplier, attempt - 1));
            long delayMs = delaySec * 1000;

            log.warn("Task execution FAILED attempt {}/{}. Scheduling retry {} in {}s. Error: {}",
                    attempt, maxRetries, nextAttempt, delaySec, cause.getMessage());

            String sql = "UPDATE task_executions SET status = ?, error_message = ?, response_status = ?, attempt = ? WHERE id = ?";
            jdbcTemplate.update(sql, ExecutionStatus.RETRYING.name(), cause.getMessage(), statusCode, attempt, payload.getExecutionId());

            payload.setCurrentAttempt(nextAttempt);

            rabbitTemplate.convertAndSend(RETRY_EXCHANGE, RETRY_ROUTING_KEY, payload, message -> {
                message.getMessageProperties().setExpiration(String.valueOf(delayMs));
                return message;
            });
        } else {
            log.error("Task execution FAILED permanently after {} attempts [Execution ID: {}]. Error: {}",
                    attempt, payload.getExecutionId(), cause.getMessage());

            String sql = "UPDATE task_executions SET status = ?, error_message = ?, response_status = ?, completed_at = ?, attempt = ? WHERE id = ?";
            jdbcTemplate.update(sql, ExecutionStatus.FAILED.name(), cause.getMessage(), statusCode, OffsetDateTime.now(), attempt, payload.getExecutionId());
        }
    }
}
