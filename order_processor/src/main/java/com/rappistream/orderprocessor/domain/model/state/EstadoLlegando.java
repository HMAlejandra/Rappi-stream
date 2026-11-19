package com.rappistream.orderprocessor.domain.model.state;

import com.rappistream.orderprocessor.domain.model.Pedido;

/**
 * Estado LLEGANDO â€” el repartidor estÃ¡ en el destino.
 *
 * Transiciones permitidas:
 *   LLEGANDO â”€â”€â–º ENTREGADO (via completarEntrega)
 */
public class EstadoLlegando implements OrderState {

    @Override
    public void asignarRepartidor(Pedido pedido, String driverId, String driverName) {
        throw new IllegalStateException(
                "No se puede reasignar repartidor: el pedido estÃ¡ en estado LLEGANDO.");
    }

    @Override
    public void iniciarEntrega(Pedido pedido) {
        throw new IllegalStateException(
                "La entrega ya fue iniciada: el pedido estÃ¡ en estado LLEGANDO.");
    }

    @Override
    public void registrarLlegada(Pedido pedido) {
        throw new IllegalStateException(
                "La llegada ya fue registrada: el pedido estÃ¡ en estado LLEGANDO.");
    }

    @Override
    public void completarEntrega(Pedido pedido) {
        // TransiciÃ³n vÃ¡lida: LLEGANDO â†’ ENTREGADO
        pedido.setEstado(new EstadoEntregado());
    }

    @Override
    public String nombre() {
        return "LLEGANDO";
    }
}

