package com.orchestrator.coordinator.controller;

import com.orchestrator.common.dto.TaskExecutionDTO;
import com.orchestrator.coordinator.entity.TaskExecutionEntity;
import com.orchestrator.coordinator.repository.TaskExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/executions")
@RequiredArgsConstructor
public class TaskExecutionController {

    private final TaskExecutionRepository executionRepository;

    @GetMapping
    public Page<TaskExecutionDTO> getAllExecutions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return executionRepository.findAll(pageable).map(this::toDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskExecutionDTO> getExecutionById(@PathVariable String id) {
        return executionRepository.findById(id)
                .map(entity -> ResponseEntity.ok(toDTO(entity)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/schedule/{scheduleId}")
    public Page<TaskExecutionDTO> getExecutionsBySchedule(
            @PathVariable String scheduleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("startedAt").descending());
        return executionRepository.findByTaskScheduleId(scheduleId, pageable).map(this::toDTO);
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
