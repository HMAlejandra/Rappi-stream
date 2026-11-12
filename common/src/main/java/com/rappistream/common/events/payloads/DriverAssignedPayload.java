package com.rappistream.common.events.payloads;

import java.util.Objects;

/**
 * Payload del evento DRIVER_ASSIGNED.
 * Se serializa como JSON dentro de OrderEventEnvelope.payload.
 *
 * Emitido por: order_processor
 * Consumido por: driver_simulator, notification_router, dashboard_aggregator
 */
public record DriverAssignedPayload(

        /** Identificador único del repartidor asignado. */
        String driverId,

        /** Nombre legible del repartidor para mostrar en el dashboard. */
        String driverName

) {
    public DriverAssignedPayload {
        Objects.requireNonNull(driverId, "driverId no puede ser nulo");
        Objects.requireNonNull(driverName, "driverName no puede ser nulo");

        if (driverId.isBlank()) {
            throw new IllegalArgumentException("driverId no puede estar vacío");
        }
        if (driverName.isBlank()) {
            throw new IllegalArgumentException("driverName no puede estar vacío");
        }
    }
}
