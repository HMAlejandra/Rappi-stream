package com.rappistream.orderprocessor.domain.model.state;

import com.rappistream.orderprocessor.domain.model.Pedido;

/**
 * Estado CREADO â€” estado inicial del pedido.
 *
 * Transiciones permitidas:
 *   CREADO â”€â”€â–º ASIGNADO (via asignarRepartidor)
 *
 * Todas las demÃ¡s operaciones son invÃ¡lidas y lanzan
 * IllegalStateException inmediatamente (Fail-Fast).
 */
public class EstadoCreado implements OrderState {

    @Override
    public void asignarRepartidor(Pedido pedido, String driverId, String driverName) {
        // TransiciÃ³n vÃ¡lida: CREADO â†’ ASIGNADO
        pedido.aplicarAsignacion(driverId, driverName);
        pedido.setEstado(new EstadoAsignado());
    }

    @Override
    public void iniciarEntrega(Pedido pedido) {
        throw new IllegalStateException(
                "No se puede iniciar la entrega: el pedido estÃ¡ en estado CREADO. " +
                "Debe asignarse un repartidor primero.");
    }

    @Override
    public void registrarLlegada(Pedido pedido) {
        throw new IllegalStateException(
                "No se puede registrar llegada: el pedido estÃ¡ en estado CREADO.");
    }

    @Override
    public void completarEntrega(Pedido pedido) {
        throw new IllegalStateException(
                "No se puede completar la entrega: el pedido estÃ¡ en estado CREADO.");
    }

    @Override
    public String nombre() {
        return "CREADO";
    }
}

