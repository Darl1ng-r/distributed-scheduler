package com.orchestrator.coordinator.controller;

import com.orchestrator.common.dto.TaskScheduleDTO;
import com.orchestrator.common.enums.TaskStatus;
import com.orchestrator.coordinator.entity.TaskScheduleEntity;
import com.orchestrator.coordinator.repository.TaskScheduleRepository;
import com.orchestrator.coordinator.service.TaskSchedulerEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class TaskScheduleController {

    private final TaskScheduleRepository scheduleRepository;
    private final TaskSchedulerEngine schedulerEngine;

    @GetMapping
    public Page<TaskScheduleDTO> getAllSchedules(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return scheduleRepository.findAll(pageable).map(this::toDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskScheduleDTO> getScheduleById(@PathVariable String id) {
        return scheduleRepository.findById(id)
                .map(entity -> ResponseEntity.ok(toDTO(entity)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<TaskScheduleDTO> createSchedule(@Valid @RequestBody TaskScheduleDTO dto) {
        TaskScheduleEntity entity = toEntity(dto);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
        }
        TaskScheduleEntity saved = scheduleRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskScheduleDTO> updateSchedule(@PathVariable String id, @Valid @RequestBody TaskScheduleDTO dto) {
        return scheduleRepository.findById(id)
                .map(existing -> {
                    existing.setName(dto.getName());
                    existing.setCronExpression(dto.getCronExpression());
                    existing.setWebhookUrl(dto.getWebhookUrl());
                    existing.setHeaders(dto.getHeaders());
                    if (dto.getMaxRetries() != null) existing.setMaxRetries(dto.getMaxRetries());
                    if (dto.getBackoffMultiplier() != null) existing.setBackoffMultiplier(dto.getBackoffMultiplier());
                    if (dto.getInitialIntervalSec() != null) existing.setInitialIntervalSec(dto.getInitialIntervalSec());
                    if (dto.getStatus() != null) existing.setStatus(dto.getStatus());
                    TaskScheduleEntity updated = scheduleRepository.save(existing);
                    return ResponseEntity.ok(toDTO(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TaskScheduleDTO> updateStatus(@PathVariable String id, @RequestParam TaskStatus status) {
        return scheduleRepository.findById(id)
                .map(existing -> {
                    existing.setStatus(status);
                    TaskScheduleEntity updated = scheduleRepository.save(existing);
                    return ResponseEntity.ok(toDTO(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable String id) {
        if (!scheduleRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        scheduleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/trigger")
    public ResponseEntity<Map<String, String>> triggerScheduleNow(@PathVariable String id) {
        if (!scheduleRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        String executionId = schedulerEngine.triggerTaskNow(id);
        return ResponseEntity.accepted().body(Map.of(
                "message", "Schedule triggered successfully",
                "executionId", executionId,
                "scheduleId", id
        ));
    }

    private TaskScheduleDTO toDTO(TaskScheduleEntity entity) {
        return TaskScheduleDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .cronExpression(entity.getCronExpression())
                .webhookUrl(entity.getWebhookUrl())
                .headers(entity.getHeaders())
                .maxRetries(entity.getMaxRetries())
                .backoffMultiplier(entity.getBackoffMultiplier())
                .initialIntervalSec(entity.getInitialIntervalSec())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private TaskScheduleEntity toEntity(TaskScheduleDTO dto) {
        return TaskScheduleEntity.builder()
                .id(dto.getId())
                .name(dto.getName())
                .cronExpression(dto.getCronExpression())
                .webhookUrl(dto.getWebhookUrl())
                .headers(dto.getHeaders())
                .maxRetries(dto.getMaxRetries())
                .backoffMultiplier(dto.getBackoffMultiplier())
                .initialIntervalSec(dto.getInitialIntervalSec())
                .status(dto.getStatus() != null ? dto.getStatus() : TaskStatus.ACTIVE)
                .build();
    }
}
