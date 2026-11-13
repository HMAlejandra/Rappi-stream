package com.rappistream.pushworker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración RabbitMQ para push_notification_worker (consumidor).
 *
 * Declara la cola y el binding idempotentemente.
 * Jackson deserializa automáticamente a NotificationCommand.
 */
@Configuration
public class RabbitMqConfig {

    public static final String DRIVER_PUSH_QUEUE      = "driver_push_queue";
    public static final String NOTIFICATIONS_EXCHANGE = "notifications";

    @Bean
    public TopicExchange notificationsExchange() {
        return new TopicExchange(NOTIFICATIONS_EXCHANGE, true, false);
    }

    @Bean
    public Queue driverPushQueue() {
        return new Queue(DRIVER_PUSH_QUEUE, true);
    }

    @Bean
    public Binding pushBinding(Queue driverPushQueue, TopicExchange notificationsExchange) {
        return BindingBuilder.bind(driverPushQueue)
                .to(notificationsExchange)
                .with("notify.driver.*");
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }
}
