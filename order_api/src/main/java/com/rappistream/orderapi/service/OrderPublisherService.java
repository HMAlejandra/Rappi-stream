package com.rappistream.orderapi.service;

import com.rappistream.common.commands.OrderCreatedCommand;
import com.rappistream.orderapi.config.RabbitMqConfig;
import com.rappistream.orderapi.dto.CreateOrderRequest;
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
 * SRP: solo tiene una responsabilidad — publicar comandos.
 * No valida el request (eso lo hace @Valid en el controller).
 * No conoce detalles de HTTP.
 *
 * Flujo:
 *   CreateOrderRequest (HTTP) → OrderCreatedCommand → new_orders_queue (RabbitMQ)
 *
 * El orderId se genera aquí con UUID para garantizar unicidad
 * antes de que el comando entre al sistema.
 */
@Service
public class OrderPublisherService {

    private static final Logger log = LoggerFactory.getLogger(OrderPublisherService.class);

    private final RabbitTemplate rabbitTemplate;

    public OrderPublisherService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Construye y publica un OrderCreatedCommand en new_orders_queue.
     *
     * @param request el DTO ya validado por el controller
     * @return el orderId generado (para devolver al cliente en la respuesta)
     */
    public String publicarComando(CreateOrderRequest request) {
        String orderId    = UUID.randomUUID().toString();
        String commandId  = UUID.randomUUID().toString();

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

        log.info("[order_api] OrderCreatedCommand publicado — orderId={} customer='{}'",
                orderId, request.customerName());

        return orderId;
    }
}
