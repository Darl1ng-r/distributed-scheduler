package com.orchestrator.coordinator.repository;

import com.orchestrator.common.enums.OutboxStatus;
import com.orchestrator.coordinator.entity.OutboxMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, String> {
    List<OutboxMessageEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
}
