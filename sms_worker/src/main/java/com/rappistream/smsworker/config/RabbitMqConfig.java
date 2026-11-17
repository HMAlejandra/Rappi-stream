package com.rappistream.smsworker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración RabbitMQ para sms_worker (consumidor).
 *
 * <p>La topología del exchange y la cola (notifications exchange,
 * client_sms_queue, binding notify.client.*) es declarada de forma
 * autoritativa por notification_router/config/RabbitMqConfig.java.
 * Los workers sólo necesitan configurar Jackson para deserializar
 * NotificationCommand y el listener container factory.</p>
 *
 * <p>RabbitMQ es idempotente en las declaraciones, pero declararlas
 * en múltiples servicios crea acoplamiento estructural innecesario:
 * un cambio en el exchange name o routing key requeriría modificar
 * N servicios en lugar de uno solo.</p>
 */
@Configuration
public class RabbitMqConfig {

    /** Nombre de la cola a la que este worker escucha. */
    public static final String CLIENT_SMS_QUEUE = "client_sms_queue";

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
