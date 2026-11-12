package com.rappistream.common.enums;

/**
 * Tipos de eventos del log inmutable de Kafka.
 *
 * Sistemas Distribuidos — Event Sourcing:
 * Kafka actúa como System of Record. Cada valor de este enum
 * representa un hecho ocurrido en el sistema que es inmutable
 * una vez publicado. Los consumidores reconstruyen el estado
 * actual procesando la secuencia de eventos.
 *
 * El order_id como clave de partición garantiza que todos los
 * eventos de un mismo pedido lleguen a la misma partición
 * y sean procesados en orden estricto.
 */
public enum OrderEventType {

    /**
     * Emitido por: order_processor
     * Significado: Se registró la intención de crear el pedido.
     * Siguiente estado esperado: ASIGNADO (tras DRIVER_ASSIGNED).
     */
    ORDER_CREATED,

    /**
     * Emitido por: order_processor
     * Significado: Se asignó un repartidor al pedido.
     * Consumidores reactivos: driver_simulator, notification_router,
     *                          dashboard_aggregator.
     */
    DRIVER_ASSIGNED,

    /**
     * Emitido por: driver_simulator
     * Significado: El repartidor inició el recorrido hacia el cliente.
     * Consumidores reactivos: notification_router, dashboard_aggregator.
     */
    DELIVERY_STARTED,

    /**
     * Emitido por: driver_simulator
     * Significado: El repartidor está llegando al destino.
     * Consumidores reactivos: notification_router (SMS al cliente),
     *                          dashboard_aggregator.
     */
    DRIVER_ARRIVING,

    /**
     * Emitido por: driver_simulator
     * Significado: El pedido fue entregado. Evento terminal.
     * Consumidores reactivos: notification_router (Push al repartidor),
     *                          dashboard_aggregator (actualiza PG).
     */
    ORDER_DELIVERED
}
