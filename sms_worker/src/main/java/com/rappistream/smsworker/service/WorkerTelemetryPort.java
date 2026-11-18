package com.rappistream.smsworker.service;

import com.rappistream.common.commands.WorkerActivitySignal;

/**
 * Puerto de publicación de telemetría de réplica (DIP).
 *
 * Desacopla el registro de actividad del mecanismo concreto (RabbitMQ).
 * Permite probar WorkerActivityRecorder con un mock sin tocar el broker.
 */
public interface WorkerTelemetryPort {

    /** Publica la señal de actividad de esta réplica hacia el dashboard. */
    void reportar(WorkerActivitySignal signal);
}
