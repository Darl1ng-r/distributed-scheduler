package com.orchestrator.worker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DomainRateLimiterService {

    private final int maxConcurrentPerDomain;
    private final ConcurrentHashMap<String, Semaphore> domainSemaphores = new ConcurrentHashMap<>();

    public DomainRateLimiterService(@Value("${worker.max-concurrent-per-domain:10}") int maxConcurrentPerDomain) {
        this.maxConcurrentPerDomain = maxConcurrentPerDomain;
    }

    public boolean tryAcquire(String webhookUrl, long timeoutMs) throws InterruptedException {
        String host = extractHost(webhookUrl);
        Semaphore semaphore = domainSemaphores.computeIfAbsent(host, k -> new Semaphore(maxConcurrentPerDomain));
        boolean acquired = semaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
        if (!acquired) {
            log.warn("Rate limit exceeded for host domain '{}'. Max concurrent limits: {}", host, maxConcurrentPerDomain);
        }
        return acquired;
    }

    public void release(String webhookUrl) {
        String host = extractHost(webhookUrl);
        Semaphore semaphore = domainSemaphores.get(host);
        if (semaphore != null) {
            semaphore.release();
        }
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
