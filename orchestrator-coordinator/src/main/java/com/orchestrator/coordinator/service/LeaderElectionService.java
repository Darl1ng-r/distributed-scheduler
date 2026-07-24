package com.orchestrator.coordinator.service;

import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class LeaderElectionService {

    private final RedissonClient redissonClient;
    private final String lockKey;
    private final long leaseTimeSec;
    private final String nodeId = UUID.randomUUID().toString();
    private final AtomicBoolean isLeader = new AtomicBoolean(false);

    public LeaderElectionService(RedissonClient redissonClient,
                                 @Value("${scheduler.leader-lock-key:scheduler:leader:lock}") String lockKey,
                                 @Value("${scheduler.leader-lock-lease-sec:15}") long leaseTimeSec) {
        this.redissonClient = redissonClient;
        this.lockKey = lockKey;
        this.leaseTimeSec = leaseTimeSec;
    }

    public synchronized boolean tryAcquireOrRenewLeaderLock() {
        try {
            RBucket<String> bucket = redissonClient.getBucket(lockKey);
            String currentLeader = bucket.get();

            if (currentLeader == null) {
                boolean acquired = bucket.setIfAbsent(nodeId, Duration.ofSeconds(leaseTimeSec));
                if (acquired) {
                    if (!isLeader.get()) {
                        log.info("Leader election SUCCESS: Node {} acquired leader lock key '{}'", nodeId, lockKey);
                        isLeader.set(true);
                    }
                    return true;
                }
            } else if (nodeId.equals(currentLeader)) {
                bucket.expire(Duration.ofSeconds(leaseTimeSec));
                if (!isLeader.get()) {
                    log.info("Leader election RENEWED: Node {} renewed leader lock key '{}'", nodeId, lockKey);
                    isLeader.set(true);
                }
                return true;
            }

            if (isLeader.get()) {
                log.warn("Leader election LOST: Node {} lost leader lock key '{}'", nodeId, lockKey);
                isLeader.set(false);
            }
            return false;
        } catch (Exception e) {
            log.error("Error during leader lock acquisition for node {}: {}", nodeId, e.getMessage());
            isLeader.set(false);
            return false;
        }
    }

    public boolean isCurrentLeader() {
        return isLeader.get();
    }

    @PreDestroy
    public synchronized void releaseLeadership() {
        if (isLeader.get()) {
            log.info("Node shutting down: Releasing leader lock key '{}' for node {}", lockKey, nodeId);
            try {
                RBucket<String> bucket = redissonClient.getBucket(lockKey);
                if (nodeId.equals(bucket.get())) {
                    bucket.delete();
                }
            } catch (Exception e) {
                log.warn("Error releasing leader lock on shutdown: {}", e.getMessage());
            } finally {
                isLeader.set(false);
            }
        }
    }
}

