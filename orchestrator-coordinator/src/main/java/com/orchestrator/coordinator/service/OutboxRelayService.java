package com.orchestrator.coordinator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orchestrator.common.dto.TaskMessagePayload;
import com.orchestrator.common.enums.OutboxStatus;
import com.orchestrator.coordinator.entity.OutboxMessageEntity;
import com.orchestrator.coordinator.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayService {

    private final OutboxMessageRepository outboxRepository;
    private final TaskPublisherService publisherService;
    private final ObjectMapper objectMapper;
    private final LeaderElectionService leaderElectionService;

    @Scheduled(fixedDelayString = "${scheduler.outbox-relay-interval-ms:1000}")
    @Transactional
    public void relayOutboxMessages() {
        if (!leaderElectionService.tryAcquireOrRenewLeaderLock()) {
            return;
        }

        List<OutboxMessageEntity> pendingMessages = outboxRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, 50));

        if (pendingMessages.isEmpty()) {
            return;
        }

        log.debug("Outbox relay processing {} pending messages...", pendingMessages.size());

        for (OutboxMessageEntity message : pendingMessages) {
            try {
                TaskMessagePayload payload = objectMapper.readValue(message.getPayload(), TaskMessagePayload.class);
                publisherService.publishTask(payload);
                message.setStatus(OutboxStatus.PUBLISHED);
                message.setProcessedAt(OffsetDateTime.now());
                outboxRepository.save(message);
                log.info("Outbox message [ID: {}] for aggregate ID {} successfully published to RabbitMQ", message.getId(), message.getAggregateId());
            } catch (Exception e) {
                log.error("Failed to process outbox message [ID: {}]: {}", message.getId(), e.getMessage(), e);
                message.setStatus(OutboxStatus.FAILED);
                outboxRepository.save(message);
            }
        }
    }
}
