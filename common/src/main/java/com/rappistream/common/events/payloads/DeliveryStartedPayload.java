package com.rappistream.common.events.payloads;

import java.util.Objects;

/**
 * Payload del evento DELIVERY_STARTED.
 * Se serializa como JSON dentro de OrderEventEnvelope.payload.
 *
 * Emitido por: driver_simulator
 * Consumido por: notification_router, dashboard_aggregator
 */
public record DeliveryStartedPayload(

        /** Identificador del repartidor que inició el recorrido. */
        String driverId,

        /** Tiempo estimado de llegada en segundos (para la simulación). */
        int estimatedArrivalSeconds

) {
    public DeliveryStartedPayload {
        Objects.requireNonNull(driverId, "driverId no puede ser nulo");
        if (driverId.isBlank()) {
            throw new IllegalArgumentException("driverId no puede estar vacío");
        }
        if (estimatedArrivalSeconds <= 0) {
            throw new IllegalArgumentException("estimatedArrivalSeconds debe ser mayor que cero");
        }
    }
}
