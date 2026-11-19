package com.rappistream.common.commands;

import java.time.Instant;
import java.util.Objects;

/**
 * Comando inmutable para crear un pedido.
 * Viaja por RabbitMQ: order_api → new_orders_queue → order_processor.
 * Constructor compacto aplica validación Fail-Fast.
 */
public record OrderCreatedCommand(
        String commandId,
        String orderId,
        String customerName,
        String product,
        int    quantity,
        String address,
        Instant issuedAt
) {
    public OrderCreatedCommand {
        Objects.requireNonNull(commandId, "commandId no puede ser nulo");
        Objects.requireNonNull(orderId,   "orderId no puede ser nulo");
        Objects.requireNonNull(issuedAt,  "issuedAt no puede ser nulo");
        if (commandId.isBlank())                     throw new IllegalArgumentException("commandId no puede estar vacío");
        if (orderId.isBlank())                       throw new IllegalArgumentException("orderId no puede estar vacío");
        if (customerName == null || customerName.isBlank()) throw new IllegalArgumentException("customerName no puede ser nulo o vacío");
        if (product == null || product.isBlank())    throw new IllegalArgumentException("product no puede ser nulo o vacío");
        if (quantity <= 0)                           throw new IllegalArgumentException("quantity debe ser mayor que cero, recibido: " + quantity);
        if (address == null || address.isBlank())    throw new IllegalArgumentException("address no puede ser nulo o vacío");
    }
}
