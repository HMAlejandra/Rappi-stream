package com.rappistream.orderprocessor.domain;

/**
 * Estado CREADO — estado inicial del pedido.
 * Transición permitida: CREADO ──► ASIGNADO (asignarRepartidor).
 */
public class EstadoCreado implements OrderState {

    @Override
    public void asignarRepartidor(Pedido pedido, String driverId, String driverName) {
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
    public String nombre() { return "CREADO"; }
}
