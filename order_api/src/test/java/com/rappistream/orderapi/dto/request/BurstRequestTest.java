package com.rappistream.orderapi.dto.request;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas Fail-Fast de BurstRequest (defaults del constructor compacto).
 */
class BurstRequestTest {

    @Test
    void aplicaDefaultsCuandoLosCamposOpcionalesEstanEnBlanco() {
        // Arrange / Act
        BurstRequest req = new BurstRequest(10, 2, 0L, null, "  ", "");

        // Assert
        assertEquals("Cliente Ráfaga", req.baseName());
        assertEquals("Combo Ráfaga", req.baseProduct());
        assertEquals("Calle Falsa 123", req.baseAddress());
        assertEquals(0L, req.rhythmMillis());
    }

    @Test
    void conservaLosValoresProporcionados() {
        // Arrange / Act
        BurstRequest req = new BurstRequest(25, 5, 100L, "Ana", "Pizza", "Av 10");

        // Assert
        assertEquals(25, req.count());
        assertEquals(5, req.concurrency());
        assertEquals(100L, req.rhythmMillis());
        assertEquals("Ana", req.baseName());
        assertEquals("Pizza", req.baseProduct());
    }

    @Test
    void rhythmMillisNuloDefaultaACero() {
        // Arrange / Act
        BurstRequest req = new BurstRequest(1, 1, null, "C", "P", "A");

        // Assert
        assertEquals(0L, req.rhythmMillis());
    }
}
