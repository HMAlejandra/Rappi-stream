package com.rappistream.common.dto;

import com.rappistream.common.enums.ReplicaStatus;
import com.rappistream.common.enums.WorkerType;

import java.time.Instant;

/**
 * Estado de una réplica de worker para el semáforo del dashboard.
 *
 * Publicado por WebSocket (/topic/workers) desde dashboard_backend, que lo
 * construye a partir de WorkerActivitySignal reales recibidas por RabbitMQ.
 *
 * DTO de salida: sin Fail-Fast.
 */
public record WorkerActivityDTO(

        /** Hostname/id de la réplica. */
        String replicaId,

        /** Canal del worker (SMS/PUSH). */
        WorkerType workerType,

        /** Mensajes procesados por esta réplica. */
        long processedCount,

        /** Estado del semáforo (verde/ámbar/gris). */
        ReplicaStatus status,

        /** Latencia del último procesamiento (ms). */
        long latencyMillis,

        /** Momento de la última actividad vista (UTC). */
        Instant lastSeenAt

) {}
