package com.rappistream.orderapi.service.burst;

import com.rappistream.orderapi.dto.request.CreateOrderRequest;

/**
 * Puerto de publicación de comandos de pedido (DIP).
 *
 * Desacopla la orquestación de la ráfaga del mecanismo concreto (RabbitMQ).
 * La implementación real es OrderPublisherService, que ya publica
 * OrderCreatedCommand en new_orders_queue.
 */
public interface BurstOrderPublisherPort {

    /**
     * Publica un comando de pedido y devuelve el orderId generado.
     */
    String publicarComando(CreateOrderRequest request);
}
