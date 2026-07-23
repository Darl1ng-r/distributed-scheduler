package com.orchestrator.worker.service;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DomainRateLimiterService {

    private final RedissonClient redissonClient;
    private final int maxConcurrentPerDomain;

    public DomainRateLimiterService(RedissonClient redissonClient,
                                   @Value("${worker.max-concurrent-per-domain:10}") int maxConcurrentPerDomain) {
        this.redissonClient = redissonClient;
        this.maxConcurrentPerDomain = maxConcurrentPerDomain;
    }

    public boolean tryAcquire(String webhookUrl, long timeoutMs) throws InterruptedException {
        String host = extractHost(webhookUrl);
        String lockKey = "ratelimit:domain:" + host;
        RSemaphore semaphore = redissonClient.getSemaphore(lockKey);
        semaphore.trySetPermits(maxConcurrentPerDomain);

        boolean acquired = semaphore.tryAcquire(1, timeoutMs, TimeUnit.MILLISECONDS);
        if (!acquired) {
            log.warn("Cluster domain rate limit exceeded for host '{}'. Max concurrent permit limit: {}", host, maxConcurrentPerDomain);
        }
        return acquired;
    }

    public void release(String webhookUrl) {
        String host = extractHost(webhookUrl);
        String lockKey = "ratelimit:domain:" + host;
        RSemaphore semaphore = redissonClient.getSemaphore(lockKey);
        semaphore.release();
    }

    private String extractHost(String webhookUrl) {
        try {
            URI uri = URI.create(webhookUrl);
            String host = uri.getHost();
            return host != null ? host.toLowerCase() : "default";
        } catch (Exception e) {
            return "default";
        }
    }
}
