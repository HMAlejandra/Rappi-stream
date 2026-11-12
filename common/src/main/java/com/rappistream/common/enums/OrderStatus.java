package com.rappistream.common.enums;

/**
 * Estados posibles del ciclo de vida de un pedido.
 *
 * POO — Patrón State:
 * Estos valores son los estados concretos que implementará
 * el patrón State en order_processor y dashboard_aggregator.
 * Cada estado encapsulará sus propias reglas de transición.
 *
 * Sistemas Distribuidos:
 * Este enum es el contrato de estado compartido entre todos
 * los microservicios. Kafka garantiza el orden de eventos
 * por partición (order_id), lo que asegura que los estados
 * se procesen en la secuencia correcta.
 */
public enum OrderStatus {

    /**
     * El pedido fue recibido por order_api y publicado en RabbitMQ.
     * Aún no ha sido procesado por order_processor.
     */
    CREADO,

    /**
     * order_processor procesó el comando, creó la entidad Pedido,
     * asignó un repartidor (Strategy) y persistió en PostgreSQL.
     * Evento Kafka emitido: DRIVER_ASSIGNED.
     */
    ASIGNADO,

    /**
     * driver_simulator confirmó que el repartidor inició el recorrido.
     * Evento Kafka emitido: DELIVERY_STARTED.
     */
    EN_CAMINO,

    /**
     * driver_simulator confirmó que el repartidor está llegando.
     * Evento Kafka emitido: DRIVER_ARRIVING.
     * Se dispara notificación SMS al cliente.
     */
    LLEGANDO,

    /**
     * El pedido fue entregado exitosamente.
     * Evento Kafka emitido: ORDER_DELIVERED.
     * Estado terminal — no admite más transiciones.
     * Se dispara notificación Push al repartidor.
     */
    ENTREGADO
}
