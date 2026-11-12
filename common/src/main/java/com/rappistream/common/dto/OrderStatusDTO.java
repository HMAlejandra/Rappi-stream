package com.rappistream.common.dto;

import com.rappistream.common.enums.OrderStatus;

import java.time.Instant;

/**
 * DTO inmutable para transmitir el estado consolidado de un pedido.
 *
 * Flujo:
 * dashboard_aggregator → construye → HTTP POST → dashboard_backend
 * dashboard_backend → serializa a JSON → WebSocket broadcast → browser
 *
 * POO — Separación de capas:
 * Este DTO es el contrato entre el agregador y el backend del dashboard.
 * No es una entidad de dominio ni una entidad JPA.
 * Contiene exactamente lo que el frontend necesita mostrar.
 */
public record OrderStatusDTO(

        /** Identificador único del pedido. */
        String orderId,

        /** Nombre del cliente. */
        String customerName,

        /** Producto pedido. */
        String product,

        /** Cantidad pedida. */
        int quantity,

        /** Dirección de entrega. */
        String address,

        /** Identificador del repartidor asignado. Puede ser null si aún no hay asignación. */
        String driverId,

        /** Nombre del repartidor asignado. Puede ser null si aún no hay asignación. */
        String driverName,

        /** Estado actual del pedido. */
        OrderStatus status,

        /** Momento de la última actualización de estado. */
        Instant lastUpdated

) {
    // No requiere validación Fail-Fast aquí porque es solo un DTO de salida.
    // Los invariantes se validan en el dominio antes de construir este DTO.
}
