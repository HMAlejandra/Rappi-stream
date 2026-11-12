package com.rappistream.orderprocessor.domain.state;

import com.rappistream.orderprocessor.domain.model.Pedido;

/**
 * Estado EN_CAMINO — el repartidor inició el recorrido.
 *
 * Transiciones permitidas:
 *   EN_CAMINO ──► LLEGANDO (via registrarLlegada)
 */
public class EstadoEnCamino implements OrderState {

    @Override
    public void asignarRepartidor(Pedido pedido, String driverId, String driverName) {
        throw new IllegalStateException(
                "No se puede reasignar repartidor: el pedido está EN_CAMINO.");
    }

    @Override
    public void iniciarEntrega(Pedido pedido) {
        throw new IllegalStateException(
                "La entrega ya fue iniciada: el pedido está EN_CAMINO.");
    }

    @Override
    public void registrarLlegada(Pedido pedido) {
        // Transición válida: EN_CAMINO → LLEGANDO
        pedido.setEstado(new EstadoLlegando());
    }

    @Override
    public void completarEntrega(Pedido pedido) {
        throw new IllegalStateException(
                "No se puede completar la entrega: el pedido está EN_CAMINO, " +
                "debe registrarse la llegada primero.");
    }

    @Override
    public String nombre() {
        return "EN_CAMINO";
    }
}
