package com.orchestrator.coordinator.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class LeaderElectionService {

    private final RedissonClient redissonClient;
    private final String lockKey;
    private final AtomicBoolean isLeader = new AtomicBoolean(false);
    private RLock leaderLock;

    public LeaderElectionService(RedissonClient redissonClient,
                                 @Value("${scheduler.leader-lock-key:scheduler:leader:lock}") String lockKey) {
        this.redissonClient = redissonClient;
        this.lockKey = lockKey;
    }

    @PostConstruct
    public void init() {
        this.leaderLock = redissonClient.getLock(lockKey);
    }

    public boolean tryAcquireOrRenewLeaderLock() {
        try {
            if (leaderLock.isHeldByCurrentThread()) {
                if (!isLeader.get()) {
                    isLeader.set(true);
                }
                return true;
            }

            // Using leaseTime = -1 enables Redisson Watchdog auto-renewal mechanism
            boolean acquired = leaderLock.tryLock(0, -1, TimeUnit.SECONDS);
            if (acquired) {
                if (!isLeader.get()) {
                    log.info("Leader election SUCCESS: Node acquired leader lock key '{}' with Watchdog auto-renewal", lockKey);
                    isLeader.set(true);
                }
                return true;
            } else {
                if (isLeader.get()) {
                    log.warn("Leader election LOST: Node lost leader lock key '{}'", lockKey);
                    isLeader.set(false);
                }
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            isLeader.set(false);
            return false;
        } catch (Exception e) {
            log.error("Error during Redisson leader lock acquisition: {}", e.getMessage());
            isLeader.set(false);
            return false;
        }
    }

    public boolean isCurrentLeader() {
        return isLeader.get() && leaderLock != null && leaderLock.isHeldByCurrentThread();
    }

    @PreDestroy
    public void releaseLeadership() {
        if (leaderLock != null && leaderLock.isHeldByCurrentThread()) {
            log.info("Node shutting down: Releasing leader lock key '{}'", lockKey);
            try {
                leaderLock.unlock();
            } catch (Exception e) {
                log.warn("Error releasing leader lock on shutdown: {}", e.getMessage());
            }
        }
    }
}

