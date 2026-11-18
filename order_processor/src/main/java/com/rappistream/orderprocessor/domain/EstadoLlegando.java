package com.rappistream.orderprocessor.domain;

/**
 * Estado LLEGANDO — el repartidor está en el destino.
 * Transición permitida: LLEGANDO ──► ENTREGADO (completarEntrega).
 */
public class EstadoLlegando implements OrderState {

    @Override
    public void asignarRepartidor(Pedido pedido, String driverId, String driverName) {
        throw new IllegalStateException("No se puede reasignar repartidor: el pedido está LLEGANDO.");
    }

    @Override
    public void iniciarEntrega(Pedido pedido) {
        throw new IllegalStateException("La entrega ya fue iniciada: el pedido está LLEGANDO.");
    }

    @Override
    public void registrarLlegada(Pedido pedido) {
        throw new IllegalStateException("La llegada ya fue registrada: el pedido está LLEGANDO.");
    }

    @Override
    public void completarEntrega(Pedido pedido) {
        pedido.setEstado(new EstadoEntregado());
    }

    @Override
    public String nombre() { return "LLEGANDO"; }
}
