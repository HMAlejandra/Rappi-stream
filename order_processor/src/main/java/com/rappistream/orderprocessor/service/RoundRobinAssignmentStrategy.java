package com.rappistream.orderprocessor.service;

import com.rappistream.orderprocessor.domain.Driver;
import com.rappistream.orderprocessor.domain.DriverAssignmentStrategy;
import com.rappistream.orderprocessor.domain.Pedido;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Estrategia de asignación Round-Robin.
 *
 * POO — Patrón Strategy (implementación concreta):
 * Rota la selección de repartidores disponibles en orden circular.
 *
 * Concurrencia:
 * AtomicInteger garantiza thread-safety sin bloques synchronized,
 * evitando condiciones de carrera cuando múltiples pedidos
 * llegan simultáneamente.
 */
public class RoundRobinAssignmentStrategy implements DriverAssignmentStrategy {

    private final List<Driver> repartidores;
    private final AtomicInteger cursor = new AtomicInteger(0);

    public RoundRobinAssignmentStrategy(List<Driver> repartidores) {
        if (repartidores == null || repartidores.isEmpty())
            throw new IllegalArgumentException("La lista de repartidores no puede ser nula ni vacía");
        this.repartidores = List.copyOf(repartidores);
    }

    @Override
    public void asignar(Pedido pedido) {
        List<Driver> disponibles = repartidores.stream()
                .filter(Driver::isAvailable)
                .toList();

        if (disponibles.isEmpty())
            throw new IllegalStateException(
                    "No hay repartidores disponibles para asignar al pedido: " + pedido.getId());

        int indice = Math.abs(cursor.getAndIncrement() % disponibles.size());
        Driver seleccionado = disponibles.get(indice);

        pedido.asignarRepartidor(seleccionado.getId(), seleccionado.getName());
    }
}
