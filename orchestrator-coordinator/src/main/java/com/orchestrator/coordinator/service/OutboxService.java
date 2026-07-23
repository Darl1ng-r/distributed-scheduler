package com.orchestrator.coordinator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.common.dto.TaskMessagePayload;
import com.orchestrator.common.enums.OutboxStatus;
import com.orchestrator.coordinator.entity.OutboxMessageEntity;
import com.orchestrator.coordinator.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxMessageRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void enqueueTaskExecution(String executionId, TaskMessagePayload payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            OutboxMessageEntity entity = OutboxMessageEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .aggregateType("TASK_EXECUTION")
                    .aggregateId(executionId)
                    .payload(jsonPayload)
                    .status(OutboxStatus.PENDING)
                    .createdAt(OffsetDateTime.now())
                    .build();

            outboxRepository.save(entity);
            log.debug("Enqueued task execution ID {} to outbox table", executionId);
        } catch (Exception e) {
            log.error("Error serializing TaskMessagePayload to outbox for execution ID {}: {}", executionId, e.getMessage(), e);
            throw new RuntimeException("Failed to enqueue task payload to outbox", e);
        }
    }
}
