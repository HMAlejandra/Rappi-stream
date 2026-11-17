package com.rappistream.orderprocessor.service.strategy;

import com.rappistream.orderprocessor.domain.model.Driver;
import com.rappistream.orderprocessor.domain.model.Pedido;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Estrategia de asignaciÃ³n Round-Robin.
 *
 * POO â€” PatrÃ³n Strategy (implementaciÃ³n concreta):
 * Rota la selecciÃ³n de repartidores disponibles en orden circular.
 * El pedido 1 va al repartidor 0, el pedido 2 al repartidor 1, etc.
 * Al llegar al Ãºltimo, vuelve al primero.
 *
 * Sistemas Operativos â€” Concurrencia:
 * AtomicInteger garantiza que el Ã­ndice del cursor sea thread-safe
 * cuando mÃºltiples pedidos lleguen simultÃ¡neamente. Esto evita
 * condiciones de carrera sin necesidad de bloques synchronized.
 *
 * Para el MVP: los repartidores se inyectan como lista fija desde
 * la base de datos al iniciar el servicio. En producciÃ³n real se
 * consultarÃ­a disponibilidad en tiempo real.
 */
public class RoundRobinAssignmentStrategy implements DriverAssignmentStrategy {

    private final List<Driver> repartidores;

    /**
     * Cursor circular. AtomicInteger es thread-safe.
     * Permite que mÃºltiples pedidos concurrentes obtengan
     * repartidores diferentes sin condiciones de carrera.
     */
    private final AtomicInteger cursor = new AtomicInteger(0);

    public RoundRobinAssignmentStrategy(List<Driver> repartidores) {
        if (repartidores == null || repartidores.isEmpty()) {
            throw new IllegalArgumentException(
                    "La lista de repartidores no puede ser nula ni vacÃ­a");
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

        // SelecciÃ³n circular sobre los disponibles
        int indice = Math.abs(cursor.getAndIncrement() % disponibles.size());
        Driver seleccionado = disponibles.get(indice);

        // Delega al mÃ©todo de negocio de Pedido (PatrÃ³n State valida la transiciÃ³n)
        pedido.asignarRepartidor(seleccionado.getId(), seleccionado.getName());
    }
}

