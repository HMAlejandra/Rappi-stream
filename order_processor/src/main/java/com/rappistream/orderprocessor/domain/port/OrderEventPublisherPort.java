package com.rappistream.orderprocessor.domain.port;

import com.rappistream.orderprocessor.domain.model.Pedido;

/**
 * Puerto de salida para publicar eventos de pedido en Kafka.
 *
 * DIP — Inversión de Dependencias:
 * El dominio define este contrato. La capa de infraestructura
 * (KafkaOrderEventPublisher) lo implementa.
 *
 * El servicio de aplicación (OrderProcessorService) depende de
 * esta interfaz, no de KafkaTemplate directamente. Esto permite:
 * 1. Probar el servicio sin Kafka real (usando un mock).
 * 2. Cambiar el broker (ej: de Kafka a Pulsar) sin tocar el dominio.
 * 3. Separar claramente la lógica de negocio de la infraestructura.
 *
 * Segregación de Interfaces (ISP):
 * Esta interfaz solo declara la publicación de eventos de pedido.
 * No mezcla responsabilidades de notificaciones ni persistencia.
 */
public interface OrderEventPublisherPort {

    /**
     * Publica el evento DRIVER_ASSIGNED en el topic order_events de Kafka.
     * Usa el orderId como clave de partición.
     *
     * @param pedido el pedido con el repartidor ya asignado
     */
    void publicarDriverAssigned(Pedido pedido);

    /**
     * Publica el evento ORDER_CREATED en el topic order_events de Kafka.
     * Se llama antes de asignar el repartidor para registrar la creación.
     *
     * @param pedido el pedido recién creado (en estado CREADO)
     */
    void publicarOrderCreated(Pedido pedido);
}
