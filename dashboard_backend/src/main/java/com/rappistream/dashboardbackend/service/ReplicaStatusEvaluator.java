package com.rappistream.dashboardbackend.service;

import com.rappistream.common.enums.ReplicaStatus;

/**
 * Estrategia de evaluación del estado (SLA) de una réplica (patrón Strategy).
 *
 * Permite variar la política de semáforo (por latencia, por carga, por cola)
 * sin tocar el consumidor de telemetría.
 */
public interface ReplicaStatusEvaluator {

    /**
     * Evalúa el color del semáforo dada la latencia del último mensaje y el
     * tiempo transcurrido desde la última actividad de la réplica.
     */
    ReplicaStatus evaluar(long latencyMillis, long millisDesdeUltimaActividad);
}
