package com.rappistream.orderprocessor.domain.state;

import com.rappistream.orderprocessor.domain.model.Pedido;

/**
 * Estado ASIGNADO — un repartidor fue asignado al pedido.
 *
 * Transiciones permitidas:
 *   ASIGNADO ──► EN_CAMINO (via iniciarEntrega)
 */
public class EstadoAsignado implements OrderState {

    @Override
    public void asignarRepartidor(Pedido pedido, String driverId, String driverName) {
        throw new IllegalStateException(
                "No se puede asignar repartidor: el pedido ya está en estado ASIGNADO " +
                "(repartidor: " + pedido.getDriverId() + ").");
    }

    @Override
    public void iniciarEntrega(Pedido pedido) {
        // Transición válida: ASIGNADO → EN_CAMINO
        pedido.setEstado(new EstadoEnCamino());
    }

    @Override
    public void registrarLlegada(Pedido pedido) {
        throw new IllegalStateException(
                "No se puede registrar llegada: el pedido está en estado ASIGNADO, " +
                "debe iniciar la entrega primero.");
    }

    @Override
    public void completarEntrega(Pedido pedido) {
        throw new IllegalStateException(
                "No se puede completar la entrega: el pedido está en estado ASIGNADO.");
    }

    @Override
    public String nombre() {
        return "ASIGNADO";
    }
}
