package com.rappistream.orderprocessor.domain.model.state;

import com.rappistream.orderprocessor.domain.model.Pedido;

/**
 * Estado ENTREGADO â€” estado terminal.
 *
 * No admite ninguna transiciÃ³n. El pedido completÃ³ su ciclo de vida.
 * Cualquier intento de operaciÃ³n lanza IllegalStateException.
 */
public class EstadoEntregado implements OrderState {

    @Override
    public void asignarRepartidor(Pedido pedido, String driverId, String driverName) {
        throw new IllegalStateException(
                "El pedido ya fue ENTREGADO. No admite mÃ¡s operaciones.");
    }

    @Override
    public void iniciarEntrega(Pedido pedido) {
        throw new IllegalStateException(
                "El pedido ya fue ENTREGADO. No admite mÃ¡s operaciones.");
    }

    @Override
    public void registrarLlegada(Pedido pedido) {
        throw new IllegalStateException(
                "El pedido ya fue ENTREGADO. No admite mÃ¡s operaciones.");
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

