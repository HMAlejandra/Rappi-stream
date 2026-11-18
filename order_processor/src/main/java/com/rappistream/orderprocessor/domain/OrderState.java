package com.rappistream.orderprocessor.domain;

/**
 * Interfaz del Patrón State para el ciclo de vida del Pedido.
 *
 * Transiciones válidas:
 *   CREADO ──► ASIGNADO (asignarRepartidor)
 *   ASIGNADO ──► EN_CAMINO (iniciarEntrega)
 *   EN_CAMINO ──► LLEGANDO (registrarLlegada)
 *   LLEGANDO ──► ENTREGADO (completarEntrega)
 *
 * Las transiciones inválidas lanzan IllegalStateException (Fail-Fast).
 *
 * OCP: para agregar un nuevo estado basta con crear una nueva clase
 * que implemente esta interfaz, sin modificar Pedido.
 */
public interface OrderState {

    void asignarRepartidor(Pedido pedido, String driverId, String driverName);
    void iniciarEntrega(Pedido pedido);
    void registrarLlegada(Pedido pedido);
    void completarEntrega(Pedido pedido);
    String nombre();
}
