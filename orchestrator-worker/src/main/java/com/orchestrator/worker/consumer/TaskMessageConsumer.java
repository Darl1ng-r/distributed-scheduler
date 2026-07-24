package com.orchestrator.worker.consumer;

import com.orchestrator.common.dto.TaskMessagePayload;
import com.orchestrator.worker.config.RabbitMQConfig;
import com.orchestrator.worker.service.TaskResultHandler;
import com.orchestrator.worker.service.WebhookExecutionService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskMessageConsumer {

    private final WebhookExecutionService webhookExecutionService;
    private final TaskResultHandler resultHandler;

    @RabbitListener(queues = RabbitMQConfig.TASK_QUEUE)
    public void consumeTask(@Payload TaskMessagePayload payload,
                            Channel channel,
                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("Received task message for execution ID {} (Attempt {})", payload.getExecutionId(), payload.getCurrentAttempt());

        try {
            int statusCode = webhookExecutionService.executeWebhook(payload);
            resultHandler.handleSuccess(payload, statusCode);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed executing task for execution ID {}: {}", payload.getExecutionId(), e.getMessage());
            Integer statusCode = extractStatusCode(e);
            resultHandler.handleFailure(payload, e, statusCode);
            // Ack message from current queue since retry payload (with updated attempt/expiration) has been re-enqueued to retry queue
            channel.basicAck(deliveryTag, false);
        }
    }

    private Integer extractStatusCode(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof org.springframework.web.client.RestClientResponseException rre) {
                return rre.getStatusCode().value();
            }
            current = current.getCause();
        }
        return null;
    }
}
