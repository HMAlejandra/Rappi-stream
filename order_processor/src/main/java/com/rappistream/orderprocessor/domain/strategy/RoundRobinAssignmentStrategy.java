package com.rappistream.orderprocessor.domain.strategy;

import com.rappistream.orderprocessor.domain.model.Driver;
import com.rappistream.orderprocessor.domain.model.Pedido;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Estrategia de asignación Round-Robin.
 *
 * POO — Patrón Strategy (implementación concreta):
 * Rota la selección de repartidores disponibles en orden circular.
 * El pedido 1 va al repartidor 0, el pedido 2 al repartidor 1, etc.
 * Al llegar al último, vuelve al primero.
 *
 * Sistemas Operativos — Concurrencia:
 * AtomicInteger garantiza que el índice del cursor sea thread-safe
 * cuando múltiples pedidos lleguen simultáneamente. Esto evita
 * condiciones de carrera sin necesidad de bloques synchronized.
 *
 * Para el MVP: los repartidores se inyectan como lista fija desde
 * la base de datos al iniciar el servicio. En producción real se
 * consultaría disponibilidad en tiempo real.
 */
public class RoundRobinAssignmentStrategy implements DriverAssignmentStrategy {

    private final List<Driver> repartidores;

    /**
     * Cursor circular. AtomicInteger es thread-safe.
     * Permite que múltiples pedidos concurrentes obtengan
     * repartidores diferentes sin condiciones de carrera.
     */
    private final AtomicInteger cursor = new AtomicInteger(0);

    public RoundRobinAssignmentStrategy(List<Driver> repartidores) {
        if (repartidores == null || repartidores.isEmpty()) {
            throw new IllegalArgumentException(
                    "La lista de repartidores no puede ser nula ni vacía");
        }
        this.repartidores = List.copyOf(repartidores); // defensa: copia inmutable
    }

    @Override
    public void asignar(Pedido pedido) {
        List<Driver> disponibles = repartidores.stream()
                .filter(Driver::isAvailable)
                .toList();

        if (disponibles.isEmpty()) {
            throw new IllegalStateException(
                    "No hay repartidores disponibles para asignar al pedido: " + pedido.getId());
        }

        // Selección circular sobre los disponibles
        int indice = Math.abs(cursor.getAndIncrement() % disponibles.size());
        Driver seleccionado = disponibles.get(indice);

        // Delega al método de negocio de Pedido (Patrón State valida la transición)
        pedido.asignarRepartidor(seleccionado.getId(), seleccionado.getName());
    }
}
