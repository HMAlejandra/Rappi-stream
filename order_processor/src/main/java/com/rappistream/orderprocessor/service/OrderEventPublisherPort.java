package com.rappistream.orderprocessor.service;

import com.rappistream.orderprocessor.domain.Pedido;

/**
 * Puerto de salida para publicar eventos de pedido en Kafka.
 *
 * DIP: el servicio depende de esta interfaz, no de KafkaTemplate directamente.
 * KafkaOrderEventPublisher implementa este contrato.
 */
public interface OrderEventPublisherPort {

    void publicarOrderCreated(Pedido pedido);
    void publicarDriverAssigned(Pedido pedido);
}
