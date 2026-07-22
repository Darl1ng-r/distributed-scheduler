package com.orchestrator.common.dto;

import com.orchestrator.common.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskScheduleDTO {
    private String id;

    @NotBlank(message = "Task name is required")
    private String name;

    @NotBlank(message = "Cron expression is required")
    private String cronExpression;

    @NotBlank(message = "Webhook URL is required")
    private String webhookUrl;

    private Map<String, String> headers;

    private Integer maxRetries;
    private Double backoffMultiplier;
    private Integer initialIntervalSec;

    private TaskStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
