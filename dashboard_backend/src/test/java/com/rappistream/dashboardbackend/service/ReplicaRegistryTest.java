package com.rappistream.dashboardbackend.service;

import com.rappistream.common.dto.WorkerActivityDTO;
import com.rappistream.common.enums.ReplicaStatus;
import com.rappistream.common.enums.WorkerType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas del registro de réplicas y reevaluación del semáforo (AAA).
 */
class ReplicaRegistryTest {

    @Test
    void registraYDevuelveEnSnapshot() {
        // Arrange
        ReplicaRegistry registry = new ReplicaRegistry(new LatencyBasedReplicaStatusEvaluator());
        registry.registrar(new WorkerActivityDTO("r1", WorkerType.SMS, 1,
                ReplicaStatus.ACTIVE, 10L, Instant.now()));

        // Act
        var snap = registry.snapshot();

        // Assert
        assertEquals(1, snap.size());
        assertTrue(snap.stream().anyMatch(d -> d.replicaId().equals("r1")));
    }

    @Test
    void reevaluaAIdleCuandoPasaMuchoTiempo() {
        // Arrange
        ReplicaRegistry registry = new ReplicaRegistry(new LatencyBasedReplicaStatusEvaluator());
        Instant vieja = Instant.now().minusMillis(10_000);
        registry.registrar(new WorkerActivityDTO("r1", WorkerType.PUSH, 1,
                ReplicaStatus.ACTIVE, 10L, vieja));

        // Act
        var dto = registry.snapshot().iterator().next();

        // Assert
        assertEquals(ReplicaStatus.IDLE, dto.status());
    }

    @Test
    void limpiarVaciaElRegistro() {
        // Arrange
        ReplicaRegistry registry = new ReplicaRegistry(new LatencyBasedReplicaStatusEvaluator());
        registry.registrar(new WorkerActivityDTO("r1", WorkerType.SMS, 1,
                ReplicaStatus.ACTIVE, 1L, Instant.now()));

        // Act
        registry.limpiar();

        // Assert
        assertEquals(0, registry.snapshot().size());
    }
}
