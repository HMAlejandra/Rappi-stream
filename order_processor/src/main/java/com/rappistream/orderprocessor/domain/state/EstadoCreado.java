package com.rappistream.orderprocessor.domain.state;

import com.rappistream.orderprocessor.domain.model.Pedido;

/**
 * Estado CREADO — estado inicial del pedido.
 *
 * Transiciones permitidas:
 *   CREADO ──► ASIGNADO (via asignarRepartidor)
 *
 * Todas las demás operaciones son inválidas y lanzan
 * IllegalStateException inmediatamente (Fail-Fast).
 */
public class EstadoCreado implements OrderState {

    @Override
    public void asignarRepartidor(Pedido pedido, String driverId, String driverName) {
        // Transición válida: CREADO → ASIGNADO
        pedido.aplicarAsignacion(driverId, driverName);
        pedido.setEstado(new EstadoAsignado());
    }

    @Override
    public void iniciarEntrega(Pedido pedido) {
        throw new IllegalStateException(
                "No se puede iniciar la entrega: el pedido está en estado CREADO. " +
                "Debe asignarse un repartidor primero.");
    }

    @Override
    public void registrarLlegada(Pedido pedido) {
        throw new IllegalStateException(
                "No se puede registrar llegada: el pedido está en estado CREADO.");
    }

    @Override
    public void completarEntrega(Pedido pedido) {
        throw new IllegalStateException(
                "No se puede completar la entrega: el pedido está en estado CREADO.");
    }

    @Override
    public String nombre() {
        return "CREADO";
    }
}
