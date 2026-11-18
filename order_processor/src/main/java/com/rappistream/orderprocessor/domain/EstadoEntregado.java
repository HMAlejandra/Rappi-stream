package com.rappistream.orderprocessor.domain;

/**
 * Estado ENTREGADO — estado terminal.
 * No admite ninguna transición. El pedido completó su ciclo de vida.
 */
public class EstadoEntregado implements OrderState {

    @Override
    public void asignarRepartidor(Pedido pedido, String driverId, String driverName) {
        throw new IllegalStateException("El pedido ya fue ENTREGADO. No admite más operaciones.");
    }

    @Override
    public void iniciarEntrega(Pedido pedido) {
        throw new IllegalStateException("El pedido ya fue ENTREGADO. No admite más operaciones.");
    }

    @Override
    public void registrarLlegada(Pedido pedido) {
        throw new IllegalStateException("El pedido ya fue ENTREGADO. No admite más operaciones.");
    }

    @Override
    public void completarEntrega(Pedido pedido) {
        throw new IllegalStateException("El pedido ya fue ENTREGADO. Es un estado terminal.");
    }

    @Override
    public String nombre() { return "ENTREGADO"; }
}
