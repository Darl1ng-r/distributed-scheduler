package com.orchestrator.coordinator.controller;

import com.orchestrator.common.dto.TaskExecutionDTO;
import com.orchestrator.coordinator.entity.TaskExecutionEntity;
import com.orchestrator.coordinator.repository.TaskExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/executions")
@RequiredArgsConstructor
public class TaskExecutionController {

    private final TaskExecutionRepository executionRepository;

    @GetMapping
    public List<TaskExecutionDTO> getAllExecutions() {
        return executionRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskExecutionDTO> getExecutionById(@PathVariable String id) {
        return executionRepository.findById(id)
                .map(entity -> ResponseEntity.ok(toDTO(entity)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/schedule/{scheduleId}")
    public List<TaskExecutionDTO> getExecutionsBySchedule(@PathVariable String scheduleId) {
        return executionRepository.findByTaskScheduleIdOrderByStartedAtDesc(scheduleId).stream()
                .map(this::toDTO)
                .toList();
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
