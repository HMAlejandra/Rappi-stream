package com.rappistream.orderprocessor.domain;

/**
 * Estado EN_CAMINO — el repartidor inició el recorrido.
 * Transición permitida: EN_CAMINO ──► LLEGANDO (registrarLlegada).
 */
public class EstadoEnCamino implements OrderState {

    @Override
    public void asignarRepartidor(Pedido pedido, String driverId, String driverName) {
        throw new IllegalStateException("No se puede reasignar repartidor: el pedido está EN_CAMINO.");
    }

    @Override
    public void iniciarEntrega(Pedido pedido) {
        throw new IllegalStateException("La entrega ya fue iniciada: el pedido está EN_CAMINO.");
    }

    @Override
    public void registrarLlegada(Pedido pedido) {
        pedido.setEstado(new EstadoLlegando());
    }

    @Override
    public void completarEntrega(Pedido pedido) {
        throw new IllegalStateException(
                "No se puede completar la entrega: el pedido está EN_CAMINO, " +
                "debe registrarse la llegada primero.");
    }

    @Override
    public String nombre() { return "EN_CAMINO"; }
}
