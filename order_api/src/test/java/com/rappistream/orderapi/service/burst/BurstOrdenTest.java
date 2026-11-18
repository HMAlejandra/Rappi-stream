package com.rappistream.orderapi.service.burst;

import com.rappistream.orderapi.dto.request.CreateOrderRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas del aggregate BurstOrden (patrón AAA, aisladas).
 */
class BurstOrdenTest {

    @Test
    void siguienteGeneraHastaElObjetivoYLuegoNull() {
        // Arrange
        BurstOrden orden = new BurstOrden("b1", 3, 2, 0, "C", "P", "A");

        // Act
        CreateOrderRequest r1 = orden.siguiente();
        CreateOrderRequest r2 = orden.siguiente();
        CreateOrderRequest r3 = orden.siguiente();
        CreateOrderRequest r4 = orden.siguiente();

        // Assert
        assertNotNull(r1); assertNotNull(r2); assertNotNull(r3);
        assertNull(r4);
        assertEquals(3, orden.emitidos());
        assertTrue(orden.estaCompleta());
    }

    @Test
    void losPedidosGeneradosTienenDatosVariados() {
        // Arrange
        BurstOrden orden = new BurstOrden("b2", 2, 1, 0, "Cliente", "Combo", "Calle");

        // Act
        CreateOrderRequest r1 = orden.siguiente();
        CreateOrderRequest r2 = orden.siguiente();

        // Assert
        assertEquals("Cliente #1", r1.customerName());
        assertEquals("Calle #1", r1.address());
        assertEquals("Cliente #2", r2.customerName());
        assertNotEquals(r1.customerName(), r2.customerName());
    }

    @Test
    void rechazaObjetivoInvalido() {
        // Arrange / Act / Assert
        assertThrows(IllegalArgumentException.class, () ->
                new BurstOrden("b3", 0, 1, 0, "C", "P", "A"));
    }

    @Test
    void rechazaBurstIdVacio() {
        // Arrange / Act / Assert
        assertThrows(IllegalArgumentException.class, () ->
                new BurstOrden(" ", 1, 1, 0, "C", "P", "A"));
    }
}
