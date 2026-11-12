package com.rappistream.common.events.payloads;

import java.util.Objects;

/**
 * Payload del evento DRIVER_ARRIVING.
 * Se serializa como JSON dentro de OrderEventEnvelope.payload.
 *
 * Emitido por: driver_simulator
 * Consumido por: notification_router (dispara SMS al cliente),
 *                dashboard_aggregator (actualiza estado a LLEGANDO)
 */
public record DriverArrivingPayload(

        /** Identificador del repartidor que está llegando. */
        String driverId,

        /** Nombre del repartidor (para el mensaje de notificación). */
        String driverName,

        /**
         * Descripción del tiempo estimado de llegada.
         * Ejemplo: "2 minutos", "llegando ahora".
         * Es un String descriptivo, no un timestamp exacto (MVP).
         */
        String estimatedArrival

) {
    public DriverArrivingPayload {
        Objects.requireNonNull(driverId, "driverId no puede ser nulo");
        Objects.requireNonNull(driverName, "driverName no puede ser nulo");
        Objects.requireNonNull(estimatedArrival, "estimatedArrival no puede ser nulo");

        if (driverId.isBlank()) {
            throw new IllegalArgumentException("driverId no puede estar vacío");
        }
        if (driverName.isBlank()) {
            throw new IllegalArgumentException("driverName no puede estar vacío");
        }
        if (estimatedArrival.isBlank()) {
            throw new IllegalArgumentException("estimatedArrival no puede estar vacío");
        }
    }
}
