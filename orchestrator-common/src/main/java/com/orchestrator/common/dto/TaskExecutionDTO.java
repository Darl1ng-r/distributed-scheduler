package com.orchestrator.common.dto;

import com.orchestrator.common.enums.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutionDTO {
    private String id;
    private String taskScheduleId;
    private ExecutionStatus status;
    private Integer attempt;
    private String errorMessage;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private Integer responseStatus;
}
