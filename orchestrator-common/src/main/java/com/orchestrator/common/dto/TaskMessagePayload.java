package com.orchestrator.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskMessagePayload implements Serializable {
    private String executionId;
    private String taskScheduleId;
    private String taskName;
    private String webhookUrl;
    private Map<String, String> headers;
    private int currentAttempt;
    private int maxRetries;
    private double backoffMultiplier;
    private int initialIntervalSec;
}
