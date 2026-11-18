package com.rappistream.orderapi.dto.response;

/**
 * DTO de respuesta para POST /api/orders.
 *
 * Record inmutable — proyecta solo los datos que el cliente necesita.
 * 202 Accepted: el pedido fue aceptado y encolado, no procesado aún.
 */
public record OrderAcceptedResponse(
        String orderId,
        String status,
        String message
) {}
