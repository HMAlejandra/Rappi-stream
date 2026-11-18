package com.rappistream.orderapi.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Solicitud para disparar una ráfaga de pedidos (Modo Ráfaga).
 *
 * Record inmutable con Bean Validation (capa HTTP) y Fail-Fast en el
 * constructor compacto (defaults de los campos opcionales).
 * La ráfaga publica N OrderCreatedCommand en new_orders_queue con
 * concurrencia acotada y ritmo configurable.
 */
public record BurstRequest(

        @NotNull(message = "count es obligatorio")
        @Min(value = 1, message = "count debe ser mayor o igual a 1")
        @Max(value = 500, message = "count no puede superar 500")
        Integer count,

        @NotNull(message = "concurrency es obligatorio")
        @Min(value = 1, message = "concurrency debe ser mayor o igual a 1")
        @Max(value = 20, message = "concurrency no puede superar 20")
        Integer concurrency,

        @Min(value = 0, message = "rhythmMillis no puede ser negativo")
        @Max(value = 1000, message = "rhythmMillis no puede superar 1000")
        Long rhythmMillis,

        String baseName,
        String baseProduct,
        String baseAddress

) {
    /** Fail-Fast con defaults: completa los campos opcionales en blanco. */
    public BurstRequest {
        if (rhythmMillis == null) rhythmMillis = 0L;
        if (baseName == null || baseName.isBlank()) baseName = "Cliente Ráfaga";
        if (baseProduct == null || baseProduct.isBlank()) baseProduct = "Combo Ráfaga";
        if (baseAddress == null || baseAddress.isBlank()) baseAddress = "Calle Falsa 123";
    }
}
