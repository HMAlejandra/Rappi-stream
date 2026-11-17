package com.rappistream.orderapi.application;

import com.rappistream.common.commands.OrderCreatedCommand;
import com.rappistream.orderapi.infrastructure.config.RabbitMqConfig;
import com.rappistream.orderapi.infrastructure.web.CreateOrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Servicio de aplicación — convierte un CreateOrderRequest en un
 * OrderCreatedCommand inmutable y lo publica en RabbitMQ.
 *
 * SRP: solo tiene una responsabilidad — publicar comandos.
 * No valida el request (eso lo hace @Valid en el controller).
 * No conoce detalles de HTTP.
 *
 * Flujo:
 *   CreateOrderRequest (HTTP) → OrderCreatedCommand → new_orders_queue (RabbitMQ)
 */
@Service
public class OrderPublisherService {

    private static final Logger log = LoggerFactory.getLogger(OrderPublisherService.class);

    private final RabbitTemplate rabbitTemplate;

    public OrderPublisherService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public String publicarComando(CreateOrderRequest request) {
        String orderId   = UUID.randomUUID().toString();
        String commandId = UUID.randomUUID().toString();

        OrderCreatedCommand comando = new OrderCreatedCommand(
                commandId,
                orderId,
                request.customerName(),
                request.product(),
                request.quantity(),
                request.address(),
                Instant.now()
        );

        rabbitTemplate.convertAndSend(RabbitMqConfig.NEW_ORDERS_QUEUE, comando);

        log.info("[order_api] Comando publicado — orderId={} customer='{}'",
                orderId, request.customerName());

        return orderId;
    }
}
