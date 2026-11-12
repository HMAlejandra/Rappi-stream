package com.rappistream.common.events;

import com.rappistream.common.enums.OrderEventType;

import java.time.Instant;
import java.util.Objects;

/**
 * Envoltorio inmutable para todos los eventos del log de Kafka.
 *
 * Sistemas Distribuidos — Event Sourcing:
 * Este record representa un evento inmutable en el log de Kafka.
 * Una vez publicado, nunca se modifica. Los consumidores reconstruyen
 * el estado actual procesando la secuencia de eventos en orden.
 *
 * Kafka garantiza el orden de eventos dentro de una partición.
 * Al usar orderId como clave de partición, todos los eventos de
 * un mismo pedido llegan a la misma partición y se procesan
 * en el orden exacto en que fueron producidos.
 *
 * POO — Inmutabilidad:
 * El payload se almacena como String JSON para mantener este
 * contrato estable independientemente del tipo de evento.
 * Cada consumidor deserializa el payload según el eventType.
 *
 * Idempotencia:
 * El eventId es único. dashboard_aggregator lo usa como clave
 * de unicidad en PostgreSQL para evitar procesar el mismo evento
 * dos veces si el consumidor reinicia y reprocesa desde el inicio.
 */
public record OrderEventEnvelope(

        /**
         * Identificador único del evento (UUID).
         * Clave de idempotencia — UNIQUE en la tabla order_events de PG.
         * Permite que dashboard_aggregator reprocese desde el principio
         * sin duplicar registros en la base de datos.
         */
        String eventId,

        /**
         * Identificador del pedido al que pertenece el evento.
         * CLAVE DE PARTICIÓN en Kafka — garantiza orden por pedido.
         */
        String orderId,

        /** Tipo de evento. Determina cómo deserializar el payload. */
        OrderEventType eventType,

        /** Momento en que ocurrió el evento (UTC). */
        Instant timestamp,

        /**
         * Datos específicos del evento serializados como JSON.
         * Cada eventType tiene su propio payload:
         * - DRIVER_ASSIGNED   → DriverAssignedPayload
         * - DELIVERY_STARTED  → DeliveryStartedPayload
         * - DRIVER_ARRIVING   → DriverArrivingPayload
         * - ORDER_DELIVERED   → OrderDeliveredPayload
         */
        String payload

) {

    /** Constructor compacto — Validación Fail-Fast. */
    public OrderEventEnvelope {
        Objects.requireNonNull(eventId, "eventId no puede ser nulo");
        Objects.requireNonNull(orderId, "orderId no puede ser nulo");
        Objects.requireNonNull(eventType, "eventType no puede ser nulo");
        Objects.requireNonNull(timestamp, "timestamp no puede ser nulo");
        Objects.requireNonNull(payload, "payload no puede ser nulo");

        if (eventId.isBlank()) {
            throw new IllegalArgumentException("eventId no puede estar vacío");
        }
        if (orderId.isBlank()) {
            throw new IllegalArgumentException("orderId no puede estar vacío");
        }
        if (payload.isBlank()) {
            throw new IllegalArgumentException("payload no puede estar vacío");
        }
    }
}
