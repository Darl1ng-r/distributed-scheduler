package com.orchestrator.coordinator.controller;

import com.orchestrator.common.dto.TaskExecutionDTO;
import com.orchestrator.common.dto.TaskMessagePayload;
import com.orchestrator.common.enums.ExecutionStatus;
import com.orchestrator.coordinator.entity.TaskExecutionEntity;
import com.orchestrator.coordinator.entity.TaskScheduleEntity;
import com.orchestrator.coordinator.repository.TaskExecutionRepository;
import com.orchestrator.coordinator.repository.TaskScheduleRepository;
import com.orchestrator.coordinator.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/dlq")
@RequiredArgsConstructor
public class DeadLetterQueueController {

    private final TaskExecutionRepository executionRepository;
    private final TaskScheduleRepository scheduleRepository;
    private final OutboxService outboxService;

    @GetMapping("/executions")
    public Page<TaskExecutionDTO> getFailedExecutions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("startedAt").descending());
        return executionRepository.findByStatus(ExecutionStatus.FAILED, pageable).map(this::toDTO);
    }

    @PostMapping("/executions/{executionId}/replay")
    @Transactional
    public ResponseEntity<Map<String, String>> replayExecution(@PathVariable String executionId) {
        TaskExecutionEntity execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found with ID: " + executionId));

        if (execution.getStatus() != ExecutionStatus.FAILED) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Only FAILED executions can be replayed. Current status: " + execution.getStatus()
            ));
        }

        TaskScheduleEntity schedule = scheduleRepository.findById(execution.getTaskScheduleId())
                .orElseThrow(() -> new IllegalStateException("Associated schedule not found with ID: " + execution.getTaskScheduleId()));

        execution.setStatus(ExecutionStatus.RUNNING);
        execution.setAttempt(1);
        execution.setErrorMessage(null);
        execution.setStartedAt(OffsetDateTime.now());
        execution.setCompletedAt(null);
        execution.setResponseStatus(null);
        executionRepository.save(execution);

        TaskMessagePayload payload = TaskMessagePayload.builder()
                .executionId(execution.getId())
                .taskScheduleId(schedule.getId())
                .taskName(schedule.getName())
                .webhookUrl(schedule.getWebhookUrl())
                .headers(schedule.getHeaders())
                .currentAttempt(1)
                .maxRetries(schedule.getMaxRetries())
                .backoffMultiplier(schedule.getBackoffMultiplier())
                .initialIntervalSec(schedule.getInitialIntervalSec())
                .build();

        outboxService.enqueueTaskExecution(execution.getId(), payload);
        log.info("Replayed failed execution ID {} for schedule ID {} via outbox", executionId, schedule.getId());

        return ResponseEntity.ok(Map.of(
                "message", "Execution replayed successfully",
                "executionId", executionId,
                "scheduleId", schedule.getId()
        ));
    }

    private TaskExecutionDTO toDTO(TaskExecutionEntity entity) {
        return TaskExecutionDTO.builder()
                .id(entity.getId())
                .taskScheduleId(entity.getTaskScheduleId())
                .status(entity.getStatus())
                .attempt(entity.getAttempt())
                .errorMessage(entity.getErrorMessage())
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .responseStatus(entity.getResponseStatus())
                .build();
    }
}
