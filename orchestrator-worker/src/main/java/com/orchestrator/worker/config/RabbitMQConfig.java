package com.orchestrator.worker.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String TASK_EXCHANGE = "task.direct";
    public static final String TASK_QUEUE = "task.queue";
    public static final String TASK_ROUTING_KEY = "task.dispatch";

    public static final String RETRY_EXCHANGE = "task.retry.exchange";
    public static final String RETRY_QUEUE = "task.retry.wait";
    public static final String RETRY_ROUTING_KEY = "task.retry";

    @Bean
    public DirectExchange taskExchange() {
        return new DirectExchange(TASK_EXCHANGE);
    }

    @Bean
    public DirectExchange retryExchange() {
        return new DirectExchange(RETRY_EXCHANGE);
    }

    @Bean
    public Queue taskQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", RETRY_EXCHANGE);
        args.put("x-dead-letter-routing-key", RETRY_ROUTING_KEY);
        return new Queue(TASK_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue retryWaitQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", TASK_EXCHANGE);
        args.put("x-dead-letter-routing-key", TASK_ROUTING_KEY);
        return new Queue(RETRY_QUEUE, true, false, false, args);
    }

    @Bean
    public Binding taskBinding(Queue taskQueue, DirectExchange taskExchange) {
        return BindingBuilder.bind(taskQueue).to(taskExchange).with(TASK_ROUTING_KEY);
    }

    @Bean
    public Binding retryBinding(Queue retryWaitQueue, DirectExchange retryExchange) {
        return BindingBuilder.bind(retryWaitQueue).to(retryExchange).with(RETRY_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
