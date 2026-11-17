package com.rappistream.common.events;

import com.rappistream.common.enums.OrderEventType;

import java.time.Instant;
import java.util.Objects;

/**
 * Envoltorio inmutable para todos los eventos del log de Kafka.
 * orderId es la clave de partición — garantiza orden por pedido.
 * eventId es UNIQUE en PostgreSQL — garantiza idempotencia al reprocesar.
 * payload es el JSON específico del evento (varía por eventType).
 */
public record OrderEventEnvelope(
        String        eventId,
        String        orderId,
        OrderEventType eventType,
        Instant       timestamp,
        String        payload
) {
    public OrderEventEnvelope {
        Objects.requireNonNull(eventId,    "eventId no puede ser nulo");
        Objects.requireNonNull(orderId,    "orderId no puede ser nulo");
        Objects.requireNonNull(eventType,  "eventType no puede ser nulo");
        Objects.requireNonNull(timestamp,  "timestamp no puede ser nulo");
        Objects.requireNonNull(payload,    "payload no puede ser nulo");
        if (eventId.isBlank())  throw new IllegalArgumentException("eventId no puede estar vacío");
        if (orderId.isBlank())  throw new IllegalArgumentException("orderId no puede estar vacío");
        if (payload.isBlank())  throw new IllegalArgumentException("payload no puede estar vacío");
    }
}
