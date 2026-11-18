package com.rappistream.orderprocessor.service;

import com.rappistream.orderprocessor.domain.Driver;
import com.rappistream.orderprocessor.domain.Pedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RoundRobinAssignmentStrategy — Patrón Strategy")
class RoundRobinAssignmentStrategyTest {

    private Pedido nuevoPedido(String id) {
        return new Pedido(id, "Cliente Test", "Producto", 1, "Dirección Test");
    }

    @Test @DisplayName("Debe asignar el primer repartidor disponible al primer pedido")
    void debeAsignarPrimerRepartidor() {
        List<Driver> drivers = List.of(
                new Driver("D-001", "Carlos", true),
                new Driver("D-002", "Laura",  true));
        RoundRobinAssignmentStrategy strategy = new RoundRobinAssignmentStrategy(drivers);
        Pedido pedido = nuevoPedido("order-001");
        strategy.asignar(pedido);
        assertEquals("ASIGNADO", pedido.getNombreEstado());
        assertEquals("D-001", pedido.getDriverId());
    }

    @Test @DisplayName("Debe rotar entre repartidores (Round-Robin)")
    void debeRotar() {
        List<Driver> drivers = List.of(
                new Driver("D-001", "Carlos", true),
                new Driver("D-002", "Laura",  true),
                new Driver("D-003", "Andrés", true));
        RoundRobinAssignmentStrategy strategy = new RoundRobinAssignmentStrategy(drivers);
        Pedido p1 = nuevoPedido("1"); strategy.asignar(p1);
        Pedido p2 = nuevoPedido("2"); strategy.asignar(p2);
        Pedido p3 = nuevoPedido("3"); strategy.asignar(p3);
        Pedido p4 = nuevoPedido("4"); strategy.asignar(p4);
        assertEquals("D-001", p1.getDriverId());
        assertEquals("D-002", p2.getDriverId());
        assertEquals("D-003", p3.getDriverId());
        assertEquals("D-001", p4.getDriverId());
    }

    @Test @DisplayName("Debe ignorar repartidores no disponibles")
    void debeIgnorarNoDisponibles() {
        List<Driver> drivers = List.of(
                new Driver("D-001", "Carlos", false),
                new Driver("D-002", "Laura",  true),
                new Driver("D-003", "Andrés", false));
        RoundRobinAssignmentStrategy strategy = new RoundRobinAssignmentStrategy(drivers);
        Pedido pedido = nuevoPedido("order-001");
        strategy.asignar(pedido);
        assertEquals("D-002", pedido.getDriverId());
    }

    @Test @DisplayName("Debe lanzar IllegalStateException si no hay disponibles")
    void debeLanzarSinDisponibles() {
        List<Driver> drivers = List.of(
                new Driver("D-001", "Carlos", false),
                new Driver("D-002", "Laura",  false));
        RoundRobinAssignmentStrategy strategy = new RoundRobinAssignmentStrategy(drivers);
        assertThrows(IllegalStateException.class, () -> strategy.asignar(nuevoPedido("order-001")));
    }

    @Test @DisplayName("Debe lanzar IllegalArgumentException con lista vacía")
    void debeLanzarConListaVacia() {
        assertThrows(IllegalArgumentException.class, () -> new RoundRobinAssignmentStrategy(List.of()));
    }

    @Test @DisplayName("Debe lanzar IllegalArgumentException con lista nula")
    void debeLanzarConListaNula() {
        assertThrows(IllegalArgumentException.class, () -> new RoundRobinAssignmentStrategy(null));
    }
}
