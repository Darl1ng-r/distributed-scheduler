package com.orchestrator.coordinator.repository;

import com.orchestrator.common.enums.ExecutionStatus;
import com.orchestrator.coordinator.entity.TaskExecutionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecutionEntity, String> {
    List<TaskExecutionEntity> findByTaskScheduleIdOrderByStartedAtDesc(String taskScheduleId);

    Page<TaskExecutionEntity> findByStatus(ExecutionStatus status, Pageable pageable);

    Page<TaskExecutionEntity> findByTaskScheduleId(String taskScheduleId, Pageable pageable);
}
