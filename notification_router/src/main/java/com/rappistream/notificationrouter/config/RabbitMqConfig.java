package com.rappistream.notificationrouter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ para notification_router (productor).
 *
 * Topic Exchange "notifications":
 * notification_router publica con routing keys:
 *   notify.client.sms  → llega a client_sms_queue (sms_worker)
 *   notify.driver.push → llega a driver_push_queue (push_notification_worker)
 *
 * Las colas y bindings se declaran aquí para que se creen
 * automáticamente en RabbitMQ si no existen.
 */
@Configuration
public class RabbitMqConfig {

    public static final String NOTIFICATIONS_EXCHANGE = "notifications";
    public static final String CLIENT_SMS_QUEUE       = "client_sms_queue";
    public static final String DRIVER_PUSH_QUEUE      = "driver_push_queue";
    public static final String ROUTING_KEY_SMS        = "notify.client.sms";
    public static final String ROUTING_KEY_PUSH       = "notify.driver.push";

    // --- Exchange ---
    @Bean
    public TopicExchange notificationsExchange() {
        return new TopicExchange(NOTIFICATIONS_EXCHANGE, true, false);
    }

    // --- Colas ---
    @Bean
    public Queue clientSmsQueue() {
        return new Queue(CLIENT_SMS_QUEUE, true);
    }

    @Bean
    public Queue driverPushQueue() {
        return new Queue(DRIVER_PUSH_QUEUE, true);
    }

    // --- Bindings ---
    @Bean
    public Binding smsBiding(Queue clientSmsQueue, TopicExchange notificationsExchange) {
        return BindingBuilder.bind(clientSmsQueue)
                .to(notificationsExchange)
                .with("notify.client.*");
    }

    @Bean
    public Binding pushBinding(Queue driverPushQueue, TopicExchange notificationsExchange) {
        return BindingBuilder.bind(driverPushQueue)
                .to(notificationsExchange)
                .with("notify.driver.*");
    }

    // --- Jackson ---
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
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
