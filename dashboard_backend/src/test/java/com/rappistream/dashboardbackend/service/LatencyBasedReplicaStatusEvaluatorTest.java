package com.rappistream.dashboardbackend.service;

import com.rappistream.common.enums.ReplicaStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pruebas de la estrategia de evaluación del semáforo (AAA, aisladas).
 */
class LatencyBasedReplicaStatusEvaluatorTest {

    private final LatencyBasedReplicaStatusEvaluator ev =
            new LatencyBasedReplicaStatusEvaluator();

    @Test
    void activaConLatenciaBajaYReciente() {
        // Arrange / Act
        ReplicaStatus r = ev.evaluar(10L, 100L);

        // Assert
        assertEquals(ReplicaStatus.ACTIVE, r);
    }

    @Test
    void alertaConLatenciaAlta() {
        // Arrange / Act
        ReplicaStatus r = ev.evaluar(800L, 100L);

        // Assert
        assertEquals(ReplicaStatus.ALERT, r);
    }

    @Test
    void inactivaTrasMuchoTiempoSinActividad() {
        // Arrange / Act
        ReplicaStatus r = ev.evaluar(10L, 6_000L);

        // Assert
        assertEquals(ReplicaStatus.IDLE, r);
    }

    @Test
    void inactividadTienePrioridadSobreLatencia() {
        // Arrange / Act
        ReplicaStatus r = ev.evaluar(10_000L, 6_000L);

        // Assert
        assertEquals(ReplicaStatus.IDLE, r);
    }
}
