package com.orchestrator.coordinator.repository;

import com.orchestrator.common.enums.TaskStatus;
import com.orchestrator.coordinator.entity.TaskScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskScheduleRepository extends JpaRepository<TaskScheduleEntity, String> {
    List<TaskScheduleEntity> findByStatus(TaskStatus status);
}
