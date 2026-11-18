package com.rappistream.common.dto;

import java.time.Instant;

/**
 * Contrato de respuesta al disparar una ráfaga de pedidos.
 *
 * Flujo: order_api lo produce (POST /api/orders/burst)
 *      → dashboard_backend lo parsea para iniciar la sesión de métricas.
 *
 * DTO de salida: sin Fail-Fast (los invariantes se validan en el dominio
 * antes de construirlo), igual que OrderStatusDTO.
 */
public record BurstTriggeredDTO(

        /** Identificador único de la ráfaga (UUID). */
        String burstId,

        /** Cantidad de pedidos a emitir (objetivo). */
        int emitted,

        /** Concurrencia aplicada en la publicación. */
        int concurrency,

        /** Ritmo entre publicaciones (ms). */
        long rhythmMillis,

        /** Momento de inicio de la ráfaga (UTC). */
        Instant startedAt,

        /** Mensaje descriptivo. */
        String message

) {}
