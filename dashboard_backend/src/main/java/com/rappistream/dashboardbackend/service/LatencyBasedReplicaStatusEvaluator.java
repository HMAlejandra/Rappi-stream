package com.rappistream.dashboardbackend.service;

import com.rappistream.common.enums.ReplicaStatus;
import org.springframework.stereotype.Component;

/**
 * Evalúa el semáforo por latencia y tiempo desde la última actividad.
 *
 *   Verde  → procesa y no demora.
 *   Ámbar  → latencia por encima del umbral (alta carga / demora).
 *   Gris   → inactiva más del umbral de inactividad.
 *
 * Sin if/else anidados: cada retorno corresponde a una condición simple.
 */
@Component
public class LatencyBasedReplicaStatusEvaluator implements ReplicaStatusEvaluator {

    private static final long UMBRAL_LATENCIA_MS = 500L;
    private static final long UMBRAL_INACTIVIDAD_MS = 5_000L;

    @Override
    public ReplicaStatus evaluar(long latencyMillis, long millisDesdeUltimaActividad) {
        if (millisDesdeUltimaActividad > UMBRAL_INACTIVIDAD_MS) return ReplicaStatus.IDLE;
        if (latencyMillis > UMBRAL_LATENCIA_MS) return ReplicaStatus.ALERT;
        return ReplicaStatus.ACTIVE;
    }
}
