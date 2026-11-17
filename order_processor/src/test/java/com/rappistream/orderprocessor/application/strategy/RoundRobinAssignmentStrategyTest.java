package com.rappistream.orderprocessor.application.strategy;

import com.rappistream.orderprocessor.domain.model.Driver;
import com.rappistream.orderprocessor.domain.model.Pedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias del Patrón Strategy — RoundRobinAssignmentStrategy.
 * Sin Spring, sin base de datos. Patrón AAA.
 */
@DisplayName("RoundRobinAssignmentStrategy — Patrón Strategy")
class RoundRobinAssignmentStrategyTest {

    private Pedido nuevoPedido(String id) {
        return new Pedido(id, "Cliente Test", "Producto", 1, "Dirección Test");
    }

    @Test
    @DisplayName("Debe asignar el primer repartidor disponible al primer pedido")
    void debeAsignarPrimerRepartidorAlPrimerPedido() {
        List<Driver> drivers = List.of(
                new Driver("D-001", "Carlos", true),
                new Driver("D-002", "Laura",  true));
        RoundRobinAssignmentStrategy strategy = new RoundRobinAssignmentStrategy(drivers);
        Pedido pedido = nuevoPedido("order-001");

        strategy.asignar(pedido);

        assertEquals("ASIGNADO", pedido.getNombreEstado());
        assertEquals("D-001",    pedido.getDriverId());
        assertEquals("Carlos",   pedido.getDriverName());
    }

    @Test
    @DisplayName("Debe rotar entre repartidores (Round-Robin)")
    void debeRotarEntreRepartidores() {
        List<Driver> drivers = List.of(
                new Driver("D-001", "Carlos", true),
                new Driver("D-002", "Laura",  true),
                new Driver("D-003", "Andrés", true));
        RoundRobinAssignmentStrategy strategy = new RoundRobinAssignmentStrategy(drivers);

        Pedido p1 = nuevoPedido("order-001"); strategy.asignar(p1);
        Pedido p2 = nuevoPedido("order-002"); strategy.asignar(p2);
        Pedido p3 = nuevoPedido("order-003"); strategy.asignar(p3);
        Pedido p4 = nuevoPedido("order-004"); strategy.asignar(p4);

        assertEquals("D-001", p1.getDriverId());
        assertEquals("D-002", p2.getDriverId());
        assertEquals("D-003", p3.getDriverId());
        assertEquals("D-001", p4.getDriverId(), "Debe volver al primer repartidor (circular)");
    }

    @Test
    @DisplayName("Debe ignorar repartidores no disponibles")
    void debeIgnorarRepartidoresNoDisponibles() {
        List<Driver> drivers = List.of(
                new Driver("D-001", "Carlos", false),
                new Driver("D-002", "Laura",  true),
                new Driver("D-003", "Andrés", false));
        RoundRobinAssignmentStrategy strategy = new RoundRobinAssignmentStrategy(drivers);
        Pedido pedido = nuevoPedido("order-001");

        strategy.asignar(pedido);

        assertEquals("D-002", pedido.getDriverId());
        assertEquals("Laura", pedido.getDriverName());
    }

    @Test
    @DisplayName("Debe lanzar IllegalStateException si no hay repartidores disponibles")
    void debeLanzarExcepcionSinRepartidoresDisponibles() {
        List<Driver> drivers = List.of(
                new Driver("D-001", "Carlos", false),
                new Driver("D-002", "Laura",  false));
        RoundRobinAssignmentStrategy strategy = new RoundRobinAssignmentStrategy(drivers);
        Pedido pedido = nuevoPedido("order-001");

        var ex = assertThrows(IllegalStateException.class, () -> strategy.asignar(pedido));
        assertTrue(ex.getMessage().contains("No hay repartidores disponibles"));
    }

    @Test
    @DisplayName("Debe lanzar IllegalArgumentException con lista vacía en constructor")
    void debeLanzarExcepcionConListaVacia() {
        assertThrows(IllegalArgumentException.class, () -> new RoundRobinAssignmentStrategy(List.of()));
    }

    @Test
    @DisplayName("Debe lanzar IllegalArgumentException con lista nula en constructor")
    void debeLanzarExcepcionConListaNula() {
        assertThrows(IllegalArgumentException.class, () -> new RoundRobinAssignmentStrategy(null));
    }

    @Test
    @DisplayName("El pedido debe transicionar a ASIGNADO después de la asignación")
    void pedidoDebeQuedarEnEstadoAsignado() {
        List<Driver> drivers = List.of(new Driver("D-001", "Carlos", true));
        RoundRobinAssignmentStrategy strategy = new RoundRobinAssignmentStrategy(drivers);
        Pedido pedido = nuevoPedido("order-001");
        assertEquals("CREADO", pedido.getNombreEstado());

        strategy.asignar(pedido);

        assertEquals("ASIGNADO", pedido.getNombreEstado());
        assertNotNull(pedido.getDriverId());
        assertNotNull(pedido.getDriverName());
    }
}
