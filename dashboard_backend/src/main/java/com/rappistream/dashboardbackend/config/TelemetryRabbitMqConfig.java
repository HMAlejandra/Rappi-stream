package com.rappistream.dashboardbackend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Consumidor de telemetría de réplicas (worker_telemetry_queue).
 *
 * Reutiliza el ObjectMapper del backend (JacksonConfig). El semáforo del
 * dashboard se alimenta de señales reales publicadas por sms_worker y
 * push_notification_worker, reflejando su trabajo en RabbitMQ.
 */
@Configuration
public class TelemetryRabbitMqConfig {

    public static final String WORKER_TELEMETRY_QUEUE = "worker_telemetry_queue";

    @Bean
    public Queue workerTelemetryQueue() {
        return new Queue(WORKER_TELEMETRY_QUEUE, true);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, Jackson2JsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }
}
