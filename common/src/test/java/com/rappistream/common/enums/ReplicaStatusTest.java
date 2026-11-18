package com.rappistream.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas del comportamiento del enum ReplicaStatus (semáforo).
 */
class ReplicaStatusTest {

    @Test
    void activeTieneLuzVerde() {
        // Arrange / Act / Assert
        assertEquals("verde", ReplicaStatus.ACTIVE.luz());
        assertEquals("#34C759", ReplicaStatus.ACTIVE.color());
    }

    @Test
    void alertTieneLuzAmbar() {
        // Arrange / Act / Assert
        assertEquals("ambar", ReplicaStatus.ALERT.luz());
        assertEquals("#FFCC00", ReplicaStatus.ALERT.color());
    }

    @Test
    void idleTieneLuzGris() {
        // Arrange / Act / Assert
        assertEquals("gris", ReplicaStatus.IDLE.luz());
    }

    @Test
    void losTresEstadosExisten() {
        // Arrange / Act / Assert
        assertEquals(3, ReplicaStatus.values().length);
    }
}
