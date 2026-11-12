package com.rappistream.orderprocessor.domain.strategy;

import com.rappistream.orderprocessor.domain.model.Driver;
import com.rappistream.orderprocessor.domain.model.Pedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias del Patrón Strategy — RoundRobinAssignmentStrategy.
 *
 * Estrategia de prueba:
 * Sin Spring, sin base de datos. Instanciamos directamente la Strategy
 * con una lista de drivers en memoria y verificamos el comportamiento.
 * Patrón AAA: Arrange / Act / Assert.
 */
@DisplayName("RoundRobinAssignmentStrategy — Patrón Strategy")
class RoundRobinAssignmentStrategyTest {

    private Pedido nuevoPedido(String id) {
        return new Pedido(id, "Cliente Test", "Producto", 1, "Dirección Test");
    }

    @Test
    @DisplayName("Debe asignar el primer repartidor disponible al primer pedido")
    void debeAsignarPrimerRepartidorAlPrimerPedido() {
        // Arrange
        List<Driver> drivers = List.of(
                new Driver("D-001", "Carlos", true),
                new Driver("D-002", "Laura",  true)
        );
        RoundRobinAssignmentStrategy strategy = new RoundRobinAssignmentStrategy(drivers);
        Pedido pedido = nuevoPedido("order-001");

        // Act
        strategy.asignar(pedido);

        // Assert
        assertEquals("ASIGNADO", pedido.getNombreEstado());
        assertEquals("D-001",    pedido.getDriverId());
        assertEquals("Carlos",   pedido.getDriverName());
    }

    @Test
    @DisplayName("Debe rotar entre repartidores (Round-Robin)")
    void debeRotarEntreRepartidores() {
        // Arrange
        List<Driver> drivers = List.of(
                new Driver("D-001", "Carlos", true),
                new Driver("D-002", "Laura",  true),
                new Driver("D-003", "Andrés", true)
        );
        RoundRobinAssignmentStrategy strategy = new RoundRobinAssignmentStrategy(drivers);

        // Act
        Pedido p1 = nuevoPedido("order-001");
        Pedido p2 = nuevoPedido("order-002");
        Pedido p3 = nuevoPedido("order-003");
        Pedido p4 = nuevoPedido("order-004"); // vuelve al primero

        strategy.asignar(p1);
        strategy.asignar(p2);
        strategy.asignar(p3);
        strategy.asignar(p4);

        // Assert
        assertEquals("D-001", p1.getDriverId());
        assertEquals("D-002", p2.getDriverId());
        assertEquals("D-003", p3.getDriverId());
        assertEquals("D-001", p4.getDriverId(), "Debe volver al primer repartidor (circular)");
    }

    @Test
    @DisplayName("Debe ignorar repartidores no disponibles")
    void debeIgnorarRepartidoresNoDisponibles() {
        // Arrange
        List<Driver> drivers = List.of(
                new Driver("D-001", "Carlos",  false), // no disponible
                new Driver("D-002", "Laura",   true),  // disponible
                new Driver("D-003", "Andrés",  false)  // no disponible
        );
        RoundRobinAssignmentStrategy strategy = new RoundRobinAssignmentStrategy(drivers);
        Pedido pedido = nuevoPedido("order-001");

        // Act
        strategy.asignar(pedido);

        // Assert: solo Laura estaba disponible
        assertEquals("D-002", pedido.getDriverId());
        assertEquals("Laura", pedido.getDriverName());
    }

    @Test
    @DisplayName("Debe lanzar IllegalStateException si no hay repartidores disponibles")
    void debeLanzarExcepcionSinRepartidoresDisponibles() {
        // Arrange
        List<Driver> drivers = List.of(
                new Driver("D-001", "Carlos", false),
                new Driver("D-002", "Laura",  false)
        );
        RoundRobinAssignmentStrategy strategy = new RoundRobinAssignmentStrategy(drivers);
        Pedido pedido = nuevoPedido("order-001");

        // Act & Assert
        var ex = assertThrows(IllegalStateException.class, () -> strategy.asignar(pedido));
        assertTrue(ex.getMessage().contains("No hay repartidores disponibles"));
    }

    @Test
    @DisplayName("Debe lanzar IllegalArgumentException con lista vacía en constructor")
    void debeLanzarExcepcionConListaVacia() {
        assertThrows(IllegalArgumentException.class, () ->
                new RoundRobinAssignmentStrategy(List.of()));
    }

    @Test
    @DisplayName("Debe lanzar NullPointerException con lista nula en constructor")
    void debeLanzarExcepcionConListaNula() {
        assertThrows(IllegalArgumentException.class, () ->
                new RoundRobinAssignmentStrategy(null));
    }

    @Test
    @DisplayName("El pedido debe transicionar a ASIGNADO después de la asignación")
    void pedidoDebeQuedarEnEstadoAsignado() {
        // Arrange
        List<Driver> drivers = List.of(new Driver("D-001", "Carlos", true));
        RoundRobinAssignmentStrategy strategy = new RoundRobinAssignmentStrategy(drivers);
        Pedido pedido = nuevoPedido("order-001");
        assertEquals("CREADO", pedido.getNombreEstado(), "Debe empezar en CREADO");

        // Act
        strategy.asignar(pedido);

        // Assert
        assertEquals("ASIGNADO", pedido.getNombreEstado());
        assertNotNull(pedido.getDriverId());
        assertNotNull(pedido.getDriverName());
    }
}
