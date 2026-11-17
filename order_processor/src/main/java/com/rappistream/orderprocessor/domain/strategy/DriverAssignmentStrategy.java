package com.rappistream.orderprocessor.domain.strategy;

import com.rappistream.orderprocessor.domain.model.Pedido;

/**
 * Contrato del Patrón Strategy para la asignación de repartidores.
 *
 * POO — Patrón Strategy:
 * Define el contrato que deben cumplir todas las estrategias de
 * asignación. El servicio de aplicación depende de esta interfaz,
 * no de ninguna implementación concreta.
 *
 * DIP — Inversión de Dependencias:
 * Los módulos de alto nivel (OrderProcessorService) dependen de
 * esta abstracción. Las implementaciones concretas la implementan.
 *
 * OCP — Abierto/Cerrado:
 * Para cambiar la política de asignación, se implementa una nueva
 * clase sin modificar el servicio que la usa.
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
