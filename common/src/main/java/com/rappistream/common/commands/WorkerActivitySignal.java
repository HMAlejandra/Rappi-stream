package com.rappistream.common.commands;

import com.rappistream.common.enums.WorkerType;

import java.time.Instant;
import java.util.Objects;

/**
 * Señal inmutable de actividad de una réplica de worker.
 *
 * Flujo: worker (sms_worker/push_notification_worker)
 *      → RabbitMQ (worker_telemetry_queue)
 *      → dashboard_backend → WebSocket (/topic/workers).
 *
 * Constructor compacto con validación Fail-Fast: protege los invariantes
 * (replicaId no vacío, conteo y latencia no negativos).
 */
public record WorkerActivitySignal(

        /** Hostname/id del contenedor que procesó el mensaje. */
        String replicaId,

        /** Canal del worker que reporta. */
        WorkerType workerType,

        /** Mensajes procesados por esta réplica desde su arranque. */
        long processedCount,

        /** Latencia de procesamiento de este mensaje (ms). */
        long latencyMillis,

        /** Momento de emisión de la señal (UTC). */
        Instant emittedAt

) {
    public WorkerActivitySignal {
        Objects.requireNonNull(replicaId, "replicaId no puede ser nulo");
        Objects.requireNonNull(workerType, "workerType no puede ser nulo");
        Objects.requireNonNull(emittedAt, "emittedAt no puede ser nulo");
        if (replicaId.isBlank())
            throw new IllegalArgumentException("replicaId no puede estar vacío");
        if (processedCount < 0)
            throw new IllegalArgumentException("processedCount no puede ser negativo");
        if (latencyMillis < 0)
            throw new IllegalArgumentException("latencyMillis no puede ser negativa");
    }
}
