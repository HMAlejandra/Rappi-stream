package com.rappistream.orderapi.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO de entrada para POST /api/orders.
 *
 * Implementado como Java Record (inmutable por diseño).
 * Las anotaciones de Bean Validation son procesadas por
 * @Valid en el controller antes de que el request llegue
 * al servicio — Fail-Fast en la capa HTTP.
 *
 * No contiene orderId: lo genera order_api internamente
 * con UUID.randomUUID() al construir el OrderCreatedCommand.
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
