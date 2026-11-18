package com.rappistream.orderapi.service;

import com.rappistream.common.commands.OrderCreatedCommand;
import com.rappistream.orderapi.config.RabbitMqConfig;
import com.rappistream.orderapi.dto.request.CreateOrderRequest;
import com.rappistream.orderapi.service.burst.BurstOrderPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Servicio que convierte un CreateOrderRequest en un
 * OrderCreatedCommand inmutable y lo publica en RabbitMQ.
 *
 * SRP: una sola responsabilidad — publicar comandos.
 * No valida el request (@Valid en el controller lo hace).
 * No conoce detalles HTTP.
 *
 * DIP: implementa BurstOrderPublisherPort para que la orquestación de la
 * ráfaga dependa de la abstracción, no de esta clase concreta.
 *
 * Flujo:
 *   CreateOrderRequest → OrderCreatedCommand → new_orders_queue (RabbitMQ)
 */
@Service
public class OrderPublisherService implements BurstOrderPublisherPort {

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
