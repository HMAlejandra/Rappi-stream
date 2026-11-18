package com.rappistream.common.dto;

import com.rappistream.common.enums.BurstStatus;

import java.time.Instant;

/**
 * Métricas en vivo de una ráfaga, publicadas por WebSocket (/topic/burst).
 *
 * dashboard_backend las construye a partir del flujo real de OrderStatusDTO
 * que llegan desde dashboard_aggregator (Kafka order_events). El conteo de
 * "procesados" refleja pedidos reales que ingresaron al sistema.
 *
 * DTO de salida: sin Fail-Fast.
 */
public record BurstMetricsDTO(

        /** Identificador de la ráfaga (null al reiniciar). */
        String burstId,

        /** Pedidos emitidos (objetivo). */
        int emitted,

        /** Pedidos procesados/ingresados en tiempo real. */
        int processed,

        /** Throughput en pedidos por segundo. */
        double throughputPerSec,

        /** Tiempo transcurrido desde el inicio (ms). */
        long elapsedMillis,

        /** Estado actual de la ráfaga. */
        BurstStatus status,

        /** Inicio de la ráfaga. */
        Instant startedAt,

        /** Última actualización de las métricas. */
        Instant lastUpdatedAt

) {}
