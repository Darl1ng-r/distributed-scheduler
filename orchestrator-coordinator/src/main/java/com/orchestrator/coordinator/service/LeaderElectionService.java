package com.orchestrator.coordinator.service;

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
    private final long leaseSec;
    private final AtomicBoolean isLeader = new AtomicBoolean(false);

    public LeaderElectionService(RedissonClient redissonClient,
                                 @Value("${scheduler.leader-lock-key:scheduler:leader:lock}") String lockKey,
                                 @Value("${scheduler.leader-lock-lease-sec:15}") long leaseSec) {
        this.redissonClient = redissonClient;
        this.lockKey = lockKey;
        this.leaseSec = leaseSec;
    }

    public boolean tryAcquireOrRenewLeaderLock() {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(0, leaseSec, TimeUnit.SECONDS);
            if (acquired) {
                if (!isLeader.get()) {
                    log.info("Leader election SUCCESS: Node acquired leader lock key '{}'", lockKey);
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
        return isLeader.get();
    }
}
