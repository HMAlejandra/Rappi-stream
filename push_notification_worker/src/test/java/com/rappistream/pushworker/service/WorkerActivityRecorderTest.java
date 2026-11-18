package com.rappistream.pushworker.service;

import com.rappistream.common.commands.WorkerActivitySignal;
import com.rappistream.common.enums.WorkerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas de WorkerActivityRecorder con un puerto mock (AAA, aisladas).
 */
class WorkerActivityRecorderTest {

    @Test
    void cuentaYReportaLaSeñalConElTipoYLatenciaIndicados() {
        // Arrange
        WorkerTelemetryPort port = mock(WorkerTelemetryPort.class);
        ReplicaIdentity identity = new ReplicaIdentity();
        WorkerActivityRecorder recorder = new WorkerActivityRecorder(port, identity);

        // Act
        recorder.registrar(WorkerType.PUSH, 9L);

        // Assert
        verify(port).reportar(argThat((WorkerActivitySignal s) ->
                s.workerType() == WorkerType.PUSH
                        && s.latencyMillis() == 9L
                        && s.processedCount() == 1L
                        && s.replicaId().equals(identity.id())));
    }

    @Test
    void incrementaElContadorEntreMensajes() {
        // Arrange
        WorkerTelemetryPort port = mock(WorkerTelemetryPort.class);
        WorkerActivityRecorder recorder = new WorkerActivityRecorder(port, new ReplicaIdentity());

        // Act
        recorder.registrar(WorkerType.PUSH, 1L);
        recorder.registrar(WorkerType.PUSH, 2L);

        // Assert
        assertEquals(2L, recorder.procesados());
        verify(port, times(2)).reportar(any());
    }

    @Test
    void replicaIdentityProduceIdNoVacio() {
        // Arrange / Act
        String id = new ReplicaIdentity().id();

        // Assert
        assertNotNull(id);
        assertFalse(id.isBlank());
    }
}
