package com.rappistream.orderprocessor.domain.strategy;

import com.rappistream.orderprocessor.domain.model.Pedido;

/**
 * Puerto de la estrategia de asignación de repartidores.
 *
 * POO — Patrón Strategy:
 * Define el contrato que deben cumplir todas las estrategias de
 * asignación. El servicio de aplicación depende de esta interfaz,
 * no de ninguna implementación concreta.
 *
 * Principio Abierto/Cerrado (OCP):
 * Para cambiar la política de asignación, se implementa una nueva
 * clase sin modificar el servicio que la usa.
 *
 * Segregación de Interfaces (ISP):
 * Esta interfaz tiene una sola responsabilidad: asignar repartidor.
 * No mezcla responsabilidades de notificación ni persistencia.
 *
 * Inversión de Dependencias (DIP):
 * Los módulos de alto nivel (OrderProcessorService) dependen de
 * esta abstracción. Las implementaciones concretas dependen de ella.
 *
 * Implementaciones disponibles:
 * - RoundRobinAssignmentStrategy: asignación circular entre repartidores disponibles
 */
public interface DriverAssignmentStrategy {

    /**
     * Asigna un repartidor disponible al pedido dado.
     *
     * @param pedido el pedido que necesita repartidor
     * @throws IllegalStateException si no hay repartidores disponibles
     */
    void asignar(Pedido pedido);
}
