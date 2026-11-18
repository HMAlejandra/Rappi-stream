package com.rappistream.smsworker.service;

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
        recorder.registrar(WorkerType.SMS, 12L);

        // Assert
        verify(port).reportar(argThat((WorkerActivitySignal s) ->
                s.workerType() == WorkerType.SMS
                        && s.latencyMillis() == 12L
                        && s.processedCount() == 1L
                        && s.replicaId().equals(identity.id())));
    }

    @Test
    void incrementaElContadorEntreMensajes() {
        // Arrange
        WorkerTelemetryPort port = mock(WorkerTelemetryPort.class);
        WorkerActivityRecorder recorder = new WorkerActivityRecorder(port, new ReplicaIdentity());

        // Act
        recorder.registrar(WorkerType.SMS, 1L);
        recorder.registrar(WorkerType.SMS, 2L);
        recorder.registrar(WorkerType.SMS, 3L);

        // Assert
        assertEquals(3L, recorder.procesados());
        verify(port, times(3)).reportar(any());
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
