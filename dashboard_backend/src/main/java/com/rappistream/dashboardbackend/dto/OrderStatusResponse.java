package com.rappistream.dashboardbackend.dto;

/**
 * DTO de respuesta para el estado de un pedido enviado por WebSocket.
 * Proyecta los datos de OrderStatusDTO hacia el frontend.
 */
public record OrderStatusResponse(
        String orderId,
        String customerName,
        String product,
        int    quantity,
        String address,
        String driverId,
        String driverName,
        String status,
        String lastUpdated
) {}
