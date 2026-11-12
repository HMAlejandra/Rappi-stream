package com.rappistream.orderprocessor.domain.state;

import com.rappistream.orderprocessor.domain.model.Pedido;

/**
 * Estado ENTREGADO — estado terminal.
 *
 * No admite ninguna transición. El pedido completó su ciclo de vida.
 * Cualquier intento de operación lanza IllegalStateException.
 */
public class EstadoEntregado implements OrderState {

    @Override
    public void asignarRepartidor(Pedido pedido, String driverId, String driverName) {
        throw new IllegalStateException(
                "El pedido ya fue ENTREGADO. No admite más operaciones.");
    }

    @Override
    public void iniciarEntrega(Pedido pedido) {
        throw new IllegalStateException(
                "El pedido ya fue ENTREGADO. No admite más operaciones.");
    }

    @Override
    public void registrarLlegada(Pedido pedido) {
        throw new IllegalStateException(
                "El pedido ya fue ENTREGADO. No admite más operaciones.");
    }

    @Override
    public void completarEntrega(Pedido pedido) {
        throw new IllegalStateException(
                "El pedido ya fue ENTREGADO. Es un estado terminal.");
    }

    @Override
    public String nombre() {
        return "ENTREGADO";
    }
}
