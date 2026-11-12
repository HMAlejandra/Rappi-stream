package com.rappistream.orderprocessor.domain.state;

import com.rappistream.orderprocessor.domain.model.Pedido;

/**
 * Estado LLEGANDO — el repartidor está en el destino.
 *
 * Transiciones permitidas:
 *   LLEGANDO ──► ENTREGADO (via completarEntrega)
 */
public class EstadoLlegando implements OrderState {

    @Override
    public void asignarRepartidor(Pedido pedido, String driverId, String driverName) {
        throw new IllegalStateException(
                "No se puede reasignar repartidor: el pedido está en estado LLEGANDO.");
    }

    @Override
    public void iniciarEntrega(Pedido pedido) {
        throw new IllegalStateException(
                "La entrega ya fue iniciada: el pedido está en estado LLEGANDO.");
    }

    @Override
    public void registrarLlegada(Pedido pedido) {
        throw new IllegalStateException(
                "La llegada ya fue registrada: el pedido está en estado LLEGANDO.");
    }

    @Override
    public void completarEntrega(Pedido pedido) {
        // Transición válida: LLEGANDO → ENTREGADO
        pedido.setEstado(new EstadoEntregado());
    }

    @Override
    public String nombre() {
        return "LLEGANDO";
    }
}
