package com.orchestrator.coordinator.service;

import com.orchestrator.common.dto.TaskMessagePayload;
import com.orchestrator.common.enums.TaskStatus;
import com.orchestrator.coordinator.entity.TaskScheduleEntity;
import com.orchestrator.coordinator.repository.TaskExecutionRepository;
import com.orchestrator.coordinator.repository.TaskScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskSchedulerEngineTest {

    @Mock
    private LeaderElectionService leaderElectionService;
    @Mock
    private TaskScheduleRepository scheduleRepository;
    @Mock
    private TaskExecutionRepository executionRepository;
    @Mock
    private OutboxService outboxService;
    @Mock
    private RedisScheduleIndexService redisIndexService;

    private TaskSchedulerEngine schedulerEngine;

    @BeforeEach
    void setUp() {
        schedulerEngine = new TaskSchedulerEngine(
                leaderElectionService,
                scheduleRepository,
                executionRepository,
                outboxService,
                redisIndexService
        );
    }

    @Test
    @DisplayName("Should skip schedule polling if current node is not leader")
    void shouldSkipPollingIfNotLeader() {
        when(leaderElectionService.tryAcquireOrRenewLeaderLock()).thenReturn(false);

        schedulerEngine.pollAndScheduleTasks();

        verifyNoInteractions(scheduleRepository, executionRepository, outboxService);
    }

    @Test
    @DisplayName("Should trigger task execution and enqueue to outbox when schedule is due")
    void shouldTriggerTaskWhenLeaderAndDue() {
        when(leaderElectionService.tryAcquireOrRenewLeaderLock()).thenReturn(true);

        TaskScheduleEntity schedule = TaskScheduleEntity.builder()
                .id("sched-101")
                .name("Test Billing Job")
                .cronExpression("* * * * * ?") // every second
                .webhookUrl("https://api.example.com/billing")
                .status(TaskStatus.ACTIVE)
                .maxRetries(3)
                .backoffMultiplier(2.0)
                .initialIntervalSec(5)
                .lastRunAt(null)
                .build();

        when(scheduleRepository.findByStatus(TaskStatus.ACTIVE)).thenReturn(List.of(schedule));

        schedulerEngine.pollAndScheduleTasks();

        verify(executionRepository, times(1)).save(any());
        verify(scheduleRepository, times(1)).save(schedule);

        ArgumentCaptor<TaskMessagePayload> payloadCaptor = ArgumentCaptor.forClass(TaskMessagePayload.class);
        verify(outboxService, times(1)).enqueueTaskExecution(anyString(), payloadCaptor.capture());

        TaskMessagePayload captured = payloadCaptor.getValue();
        assertEquals("sched-101", captured.getTaskScheduleId());
        assertEquals("https://api.example.com/billing", captured.getWebhookUrl());
        assertEquals(1, captured.getCurrentAttempt());
    }

    @Test
    @DisplayName("Should trigger task immediately on triggerTaskNow ad-hoc call")
    void shouldTriggerTaskNowAdHoc() {
        TaskScheduleEntity schedule = TaskScheduleEntity.builder()
                .id("sched-adhoc")
                .name("Adhoc Job")
                .cronExpression("0 0 12 * * ?")
                .webhookUrl("https://api.example.com/adhoc")
                .status(TaskStatus.ACTIVE)
                .maxRetries(2)
                .backoffMultiplier(1.5)
                .initialIntervalSec(3)
                .build();

        when(scheduleRepository.findById("sched-adhoc")).thenReturn(Optional.of(schedule));

        String executionId = schedulerEngine.triggerTaskNow("sched-adhoc");

        assertNotNull(executionId);
        verify(executionRepository, times(1)).save(any());
        verify(outboxService, times(1)).enqueueTaskExecution(eq(executionId), any(TaskMessagePayload.class));
    }
}
