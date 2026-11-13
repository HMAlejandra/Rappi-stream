package com.rappistream.orderapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ para order_api.
 *
 * Declara la cola new_orders_queue como durable=true:
 * los mensajes sobreviven a reinicios de RabbitMQ.
 *
 * Usa Jackson2JsonMessageConverter para que los mensajes
 * viajen como JSON legible en la cola (facilita depuración
 * desde el panel de administración de RabbitMQ).
 *
 * Flujo:
 * order_api → new_orders_queue → order_processor
 */
@Configuration
public class RabbitMqConfig {

    public static final String NEW_ORDERS_QUEUE = "new_orders_queue";

    /**
     * Declara la cola. Si ya existe en RabbitMQ, no hace nada.
     * durable=true: persiste entre reinicios del broker.
     */
    @Bean
    public Queue newOrdersQueue() {
        return new Queue(NEW_ORDERS_QUEUE, true);
    }

    /**
     * ObjectMapper con soporte para Instant (JavaTimeModule).
     * Desactiva la serialización de fechas como timestamps numéricos.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Convierte los mensajes a/desde JSON usando Jackson.
     * Registrado en el RabbitTemplate para que todos los envíos
     * sean automáticamente serializados.
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * RabbitTemplate configurado con el converter Jackson.
     * OrderPublisherService lo usa para publicar comandos.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
