package com.rappistream.pushworker.service;

import com.rappistream.common.commands.WorkerActivitySignal;
import com.rappistream.common.enums.WorkerType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registra la actividad de esta réplica: cuenta mensajes procesados y emite
 * la señal inmutable de telemetría.
 *
 * Clase enriquecida — encapsula el contador (estado) y la construcción de la
 * señal, delegando el envío al puerto (DIP). Thread-safe vía AtomicLong.
 */
@Component
public class WorkerActivityRecorder {

    private final WorkerTelemetryPort port;
    private final ReplicaIdentity identity;
    private final AtomicLong contador = new AtomicLong(0);

    public WorkerActivityRecorder(WorkerTelemetryPort port, ReplicaIdentity identity) {
        this.port = port;
        this.identity = identity;
    }

    /**
     * Cuenta un mensaje procesado y reporta la señal con su latencia.
     */
    public void registrar(WorkerType tipo, long latencyMillis) {
        long n = contador.incrementAndGet();
        WorkerActivitySignal signal = new WorkerActivitySignal(
                identity.id(), tipo, n, latencyMillis, Instant.now());
        port.reportar(signal);
    }

    long procesados() {
        return contador.get();
    }
}
