package com.rappistream.common.events.payloads;

import java.time.Instant;
import java.util.Objects;

/**
 * Payload del evento ORDER_DELIVERED.
 * Se serializa como JSON dentro de OrderEventEnvelope.payload.
 *
 * Emitido por: driver_simulator
 * Consumido por: notification_router (dispara Push al repartidor),
 *                dashboard_aggregator (estado final ENTREGADO, actualiza PG)
 */
public record OrderDeliveredPayload(

        /** Identificador del repartidor que realizó la entrega. */
        String driverId,

        /** Nombre del repartidor. */
        String driverName,

        /** Momento exacto en que se completó la entrega (UTC). */
        Instant deliveredAt

) {
    public OrderDeliveredPayload {
        Objects.requireNonNull(driverId, "driverId no puede ser nulo");
        Objects.requireNonNull(driverName, "driverName no puede ser nulo");
        Objects.requireNonNull(deliveredAt, "deliveredAt no puede ser nulo");

        if (driverId.isBlank()) {
            throw new IllegalArgumentException("driverId no puede estar vacío");
        }
        if (driverName.isBlank()) {
            throw new IllegalArgumentException("driverName no puede estar vacío");
        }
    }
}
