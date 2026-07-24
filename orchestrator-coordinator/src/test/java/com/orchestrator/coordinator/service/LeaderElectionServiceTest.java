package com.orchestrator.coordinator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaderElectionServiceTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> bucket;

    private LeaderElectionService leaderElectionService;

    @BeforeEach
    void setUp() {
        lenient().when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        leaderElectionService = new LeaderElectionService(redissonClient, "scheduler:leader:lock", 15);
    }

    @Test
    @DisplayName("Should acquire leadership when key does not exist")
    void shouldAcquireLeadershipWhenKeyNotPresent() {
        when(bucket.get()).thenReturn(null);
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);

        boolean isLeader = leaderElectionService.tryAcquireOrRenewLeaderLock();

        assertTrue(isLeader);
        assertTrue(leaderElectionService.isCurrentLeader());
    }

    @Test
    @DisplayName("Should renew leadership when key is held by this node")
    void shouldRenewLeadershipWhenKeyHeldBySelf() {
        when(bucket.get()).thenReturn(null);
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);

        leaderElectionService.tryAcquireOrRenewLeaderLock();

        // Second call when current leader matches
        when(bucket.get()).thenAnswer(invocation -> {
            // Find the nodeId that was set
            return getStoredNodeId();
        });

        boolean isLeader = leaderElectionService.tryAcquireOrRenewLeaderLock();

        assertTrue(isLeader);
        verify(bucket, times(1)).expire(any(Duration.class));
    }

    private String getStoredNodeId() {
        // Retrieve nodeId stored during setIfAbsent
        return leaderElectionService.isCurrentLeader() ? null : "other-node";
    }

    @Test
    @DisplayName("Should lose leadership when key is held by another node")
    void shouldFailLeadershipWhenKeyHeldByAnotherNode() {
        when(bucket.get()).thenReturn("another-node-id");

        boolean isLeader = leaderElectionService.tryAcquireOrRenewLeaderLock();

        assertFalse(isLeader);
        assertFalse(leaderElectionService.isCurrentLeader());
    }
}
