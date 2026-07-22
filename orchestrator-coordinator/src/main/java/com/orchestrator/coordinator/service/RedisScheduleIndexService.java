package com.orchestrator.coordinator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Collection;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisScheduleIndexService {

    public static final String DUE_TASKS_KEY = "scheduler:due_tasks";

    private final RedissonClient redissonClient;

    public void indexSchedule(String scheduleId, OffsetDateTime nextRunTime) {
        if (scheduleId == null || nextRunTime == null) return;
        long score = nextRunTime.toEpochSecond();
        RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(DUE_TASKS_KEY);
        sortedSet.add(score, scheduleId);
        log.trace("Indexed schedule ID {} in Redis ZSET with score timestamp {}", scheduleId, score);
    }

    public Collection<String> fetchAndRemoveDueSchedules(long maxEpochSecond) {
        RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(DUE_TASKS_KEY);
        // Fetch all elements with score between 0 and maxEpochSecond (inclusive)
        Collection<String> dueIds = sortedSet.valueRange(0, true, maxEpochSecond, true);
        if (!dueIds.isEmpty()) {
            sortedSet.removeAll(dueIds);
            log.debug("Fetched and removed {} due schedule IDs from Redis ZSET", dueIds.size());
        }
        return dueIds;
    }

    public void removeSchedule(String scheduleId) {
        if (scheduleId == null) return;
        RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(DUE_TASKS_KEY);
        sortedSet.remove(scheduleId);
    }
}
