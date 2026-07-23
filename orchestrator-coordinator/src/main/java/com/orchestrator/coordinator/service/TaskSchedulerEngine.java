package com.orchestrator.coordinator.service;

import com.orchestrator.common.dto.TaskMessagePayload;
import com.orchestrator.common.enums.ExecutionStatus;
import com.orchestrator.common.enums.TaskStatus;
import com.orchestrator.coordinator.entity.TaskExecutionEntity;
import com.orchestrator.coordinator.entity.TaskScheduleEntity;
import com.orchestrator.coordinator.repository.TaskExecutionRepository;
import com.orchestrator.coordinator.repository.TaskScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskSchedulerEngine {

    private final LeaderElectionService leaderElectionService;
    private final TaskScheduleRepository scheduleRepository;
    private final TaskExecutionRepository executionRepository;
    private final TaskPublisherService publisherService;
    private final RedisScheduleIndexService redisIndexService;

    @Scheduled(fixedDelayString = "${scheduler.poll-interval-ms:5000}")
    @Transactional
    public void pollAndScheduleTasks() {
        boolean isLeader = leaderElectionService.tryAcquireOrRenewLeaderLock();
        if (!isLeader) {
            log.trace("Node is not leader. Skipping schedule polling.");
            return;
        }

        log.debug("Leader active. Polling active schedules...");
        List<TaskScheduleEntity> activeSchedules = scheduleRepository.findByStatus(TaskStatus.ACTIVE);
        OffsetDateTime now = OffsetDateTime.now();

        for (TaskScheduleEntity schedule : activeSchedules) {
            try {
                if (shouldRun(schedule, now)) {
                    triggerTask(schedule, now);
                } else if (CronExpression.isValidExpression(schedule.getCronExpression())) {
                    CronExpression cron = CronExpression.parse(schedule.getCronExpression());
                    OffsetDateTime lastRun = schedule.getLastRunAt() != null ? schedule.getLastRunAt() : now;
                    OffsetDateTime nextRun = cron.next(lastRun);
                    if (nextRun != null) {
                        redisIndexService.indexSchedule(schedule.getId(), nextRun);
                    }
                }
            } catch (Exception e) {
                log.error("Error evaluating/triggering schedule ID {}: {}", schedule.getId(), e.getMessage(), e);
            }
        }
    }

    private boolean shouldRun(TaskScheduleEntity schedule, OffsetDateTime now) {
        if (!CronExpression.isValidExpression(schedule.getCronExpression())) {
            log.warn("Invalid cron expression '{}' for schedule ID {}", schedule.getCronExpression(), schedule.getId());
            return false;
        }

        CronExpression cron = CronExpression.parse(schedule.getCronExpression());
        OffsetDateTime lastRun = schedule.getLastRunAt();

        if (lastRun == null) {
            return true;
        }

        OffsetDateTime nextExpectedRun = cron.next(lastRun);
        return nextExpectedRun != null && !nextExpectedRun.isAfter(now);
    }

    private void triggerTask(TaskScheduleEntity schedule, OffsetDateTime now) {
        String executionId = UUID.randomUUID().toString();

        TaskExecutionEntity execution = TaskExecutionEntity.builder()
                .id(executionId)
                .taskScheduleId(schedule.getId())
                .status(ExecutionStatus.RUNNING)
                .attempt(1)
                .startedAt(now)
                .build();

        executionRepository.save(execution);

        schedule.setLastRunAt(now);
        scheduleRepository.save(schedule);

        TaskMessagePayload payload = TaskMessagePayload.builder()
                .executionId(executionId)
                .taskScheduleId(schedule.getId())
                .taskName(schedule.getName())
                .webhookUrl(schedule.getWebhookUrl())
                .headers(schedule.getHeaders())
                .currentAttempt(1)
                .maxRetries(schedule.getMaxRetries())
                .backoffMultiplier(schedule.getBackoffMultiplier())
                .initialIntervalSec(schedule.getInitialIntervalSec())
                .build();

        publisherService.publishTask(payload);
        log.info("Triggered task '{}' [Schedule ID: {}, Execution ID: {}]", schedule.getName(), schedule.getId(), executionId);
    }

    @Transactional
    public String triggerTaskNow(String scheduleId) {
        TaskScheduleEntity schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found with ID: " + scheduleId));
        OffsetDateTime now = OffsetDateTime.now();
        String executionId = UUID.randomUUID().toString();

        TaskExecutionEntity execution = TaskExecutionEntity.builder()
                .id(executionId)
                .taskScheduleId(schedule.getId())
                .status(ExecutionStatus.RUNNING)
                .attempt(1)
                .startedAt(now)
                .build();

        executionRepository.save(execution);
        schedule.setLastRunAt(now);
        scheduleRepository.save(schedule);

        TaskMessagePayload payload = TaskMessagePayload.builder()
                .executionId(executionId)
                .taskScheduleId(schedule.getId())
                .taskName(schedule.getName())
                .webhookUrl(schedule.getWebhookUrl())
                .headers(schedule.getHeaders())
                .currentAttempt(1)
                .maxRetries(schedule.getMaxRetries())
                .backoffMultiplier(schedule.getBackoffMultiplier())
                .initialIntervalSec(schedule.getInitialIntervalSec())
                .build();

        publisherService.publishTask(payload);
        log.info("Manual ad-hoc trigger for task '{}' [Schedule ID: {}, Execution ID: {}]", schedule.getName(), schedule.getId(), executionId);
        return executionId;
    }
}
