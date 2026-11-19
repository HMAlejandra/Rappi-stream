package com.rappistream.orderprocessor.domain.model.state;

import com.rappistream.orderprocessor.domain.model.Pedido;

/**
 * Estado EN_CAMINO â€” el repartidor iniciÃ³ el recorrido.
 *
 * Transiciones permitidas:
 *   EN_CAMINO â”€â”€â–º LLEGANDO (via registrarLlegada)
 */
public class EstadoEnCamino implements OrderState {

    @Override
    public void asignarRepartidor(Pedido pedido, String driverId, String driverName) {
        throw new IllegalStateException(
                "No se puede reasignar repartidor: el pedido estÃ¡ EN_CAMINO.");
    }

    @Override
    public void iniciarEntrega(Pedido pedido) {
        throw new IllegalStateException(
                "La entrega ya fue iniciada: el pedido estÃ¡ EN_CAMINO.");
    }

    @Override
    public void registrarLlegada(Pedido pedido) {
        // TransiciÃ³n vÃ¡lida: EN_CAMINO â†’ LLEGANDO
        pedido.setEstado(new EstadoLlegando());
    }

    @Override
    public void completarEntrega(Pedido pedido) {
        throw new IllegalStateException(
                "No se puede completar la entrega: el pedido estÃ¡ EN_CAMINO, " +
                "debe registrarse la llegada primero.");
    }

    @Override
    public String nombre() {
        return "EN_CAMINO";
    }
}

