package com.rappistream.orderprocessor.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ para order_processor (consumidor).
 *
 * Declara la misma cola que order_api (idempotente — si ya existe, no hace nada).
 * Configura el MessageConverter Jackson para deserializar automáticamente
 * los mensajes JSON a OrderCreatedCommand.
 */
@Configuration
public class RabbitMqConfig {

    public static final String NEW_ORDERS_QUEUE = "new_orders_queue";

    @Bean
    public Queue newOrdersQueue() {
        return new Queue(NEW_ORDERS_QUEUE, true);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * Configura el listener container para usar Jackson como deserializador.
     * Sin esto, Spring AMQP no sabe cómo convertir el JSON a OrderCreatedCommand.
     */
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
