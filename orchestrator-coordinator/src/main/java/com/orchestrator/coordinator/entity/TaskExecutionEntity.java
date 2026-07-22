package com.orchestrator.coordinator.entity;

import com.orchestrator.common.enums.ExecutionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "task_executions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutionEntity {

    @Id
    private String id;

    @Column(name = "task_schedule_id", nullable = false)
    private String taskScheduleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;

    @Column(nullable = false)
    private Integer attempt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "response_status")
    private Integer responseStatus;
}
