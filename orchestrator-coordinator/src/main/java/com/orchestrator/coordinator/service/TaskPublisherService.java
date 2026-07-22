package com.orchestrator.coordinator.service;

import com.orchestrator.common.dto.TaskMessagePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskPublisherService {

    public static final String TASK_EXCHANGE = "task.direct";
    public static final String TASK_ROUTING_KEY = "task.dispatch";

    private final RabbitTemplate rabbitTemplate;

    public void publishTask(TaskMessagePayload payload) {
        log.info("Publishing task execution [ID: {}] for schedule [ID: {}] to exchange '{}'",
                payload.getExecutionId(), payload.getTaskScheduleId(), TASK_EXCHANGE);
        rabbitTemplate.convertAndSend(TASK_EXCHANGE, TASK_ROUTING_KEY, payload);
    }
}
