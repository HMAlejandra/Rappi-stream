package com.rappistream.dashboardbackend.service;

import com.rappistream.common.enums.BurstStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas del aggregate BurstSession (AAA, aisladas).
 */
class BurstSessionTest {

    @Test
    void cuentaCadaPedidoUnaSolaVez() {
        // Arrange
        BurstSession s = new BurstSession("b1", 3, Instant.now());

        // Act
        boolean n1 = s.registrar("o1");
        boolean n2 = s.registrar("o2");
        boolean dup = s.registrar("o1");

        // Assert
        assertTrue(n1);
        assertTrue(n2);
        assertFalse(dup);
        assertEquals(2, s.procesados());
    }

    @Test
    void completaAlAlcanzarElObjetivo() {
        // Arrange
        BurstSession s = new BurstSession("b2", 2, Instant.now());

        // Act
        s.registrar("o1");
        s.registrar("o2");
        var dto = s.toDTO();

        // Assert
        assertTrue(s.estaCompletada());
        assertEquals(BurstStatus.COMPLETED, dto.status());
        assertEquals(2, dto.processed());
        assertEquals(2, dto.emitted());
    }

    @Test
    void rechazaParametrosInvalidos() {
        // Arrange / Act / Assert
        assertThrows(IllegalArgumentException.class, () -> new BurstSession(" ", 1, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new BurstSession("b", 0, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new BurstSession("b", 1, null));
    }

    @Test
    void throughputYElapsedSonPositivos() {
        // Arrange
        BurstSession s = new BurstSession("b3", 1, Instant.now());

        // Act
        s.registrar("o1");
        var dto = s.toDTO();

        // Assert
        assertTrue(dto.elapsedMillis() >= 1);
        assertTrue(dto.throughputPerSec() > 0);
    }
}
