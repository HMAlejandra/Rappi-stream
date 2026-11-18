package com.rappistream.orderapi.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO de entrada para POST /api/orders.
 *
 * Record inmutable con Bean Validation — Fail-Fast en la capa HTTP.
 * El orderId no viene del cliente: lo genera order_api con UUID.
 */
public record CreateOrderRequest(

        @NotBlank(message = "customerName no puede estar vacío")
        String customerName,

        @NotBlank(message = "product no puede estar vacío")
        String product,

        @NotNull(message = "quantity no puede ser nulo")
        @Min(value = 1, message = "quantity debe ser mayor que cero")
        Integer quantity,

        @NotBlank(message = "address no puede estar vacía")
        String address

) {}
