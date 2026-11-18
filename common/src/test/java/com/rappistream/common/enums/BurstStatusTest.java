package com.rappistream.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas del comportamiento del enum BurstStatus (ciclo de vida de ráfaga).
 */
class BurstStatusTest {

    @Test
    void runningNoEsTerminal() {
        // Arrange / Act / Assert
        assertFalse(BurstStatus.RUNNING.esTerminal());
        assertEquals("En curso", BurstStatus.RUNNING.etiqueta());
    }

    @Test
    void completedEsTerminal() {
        // Arrange / Act / Assert
        assertTrue(BurstStatus.COMPLETED.esTerminal());
    }

    @Test
    void alertNoEsTerminal() {
        // Arrange / Act / Assert
        assertFalse(BurstStatus.ALERT.esTerminal());
        assertEquals("Estancada", BurstStatus.ALERT.etiqueta());
    }
}
