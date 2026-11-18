package com.rappistream.common.commands;

import com.rappistream.common.enums.WorkerType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas Fail-Fast de WorkerActivitySignal (patrón AAA, aisladas).
 */
class WorkerActivitySignalTest {

    @Test
    void construyeCuandoTodosLosInvariantesSonValidos() {
        // Arrange
        Instant ahora = Instant.now();

        // Act
        WorkerActivitySignal signal = new WorkerActivitySignal(
                "replica-1", WorkerType.SMS, 5L, 12L, ahora);

        // Assert
        assertEquals("replica-1", signal.replicaId());
        assertEquals(WorkerType.SMS, signal.workerType());
        assertEquals(5L, signal.processedCount());
    }

    @Test
    void rechazaReplicaIdVacio() {
        // Arrange / Act
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new WorkerActivitySignal("", WorkerType.PUSH, 1L, 1L, Instant.now()));

        // Assert
        assertTrue(ex.getMessage().contains("replicaId"));
    }

    @Test
    void rechazaConteoNegativo() {
        // Arrange / Act
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new WorkerActivitySignal("r1", WorkerType.SMS, -1L, 1L, Instant.now()));

        // Assert
        assertTrue(ex.getMessage().contains("processedCount"));
    }

    @Test
    void rechazaLatenciaNegativa() {
        // Arrange / Act / Assert
        assertThrows(IllegalArgumentException.class, () ->
                new WorkerActivitySignal("r1", WorkerType.PUSH, 1L, -5L, Instant.now()));
    }
}
