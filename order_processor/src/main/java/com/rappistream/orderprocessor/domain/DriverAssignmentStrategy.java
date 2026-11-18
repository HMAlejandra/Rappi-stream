package com.rappistream.orderprocessor.domain;

/**
 * Contrato del Patrón Strategy para la asignación de repartidores.
 *
 * POO — DIP + Strategy:
 * El servicio depende de esta interfaz, no de ninguna implementación concreta.
 * Para cambiar la política de asignación, se implementa una nueva clase
 * sin modificar el servicio (OCP).
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
