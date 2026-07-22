package com.orchestrator.coordinator.repository;

import com.orchestrator.coordinator.entity.TaskExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecutionEntity, String> {
    List<TaskExecutionEntity> findByTaskScheduleIdOrderByStartedAtDesc(String taskScheduleId);
}
