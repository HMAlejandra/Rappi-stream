package com.rappistream.orderprocessor.domain.model.state;

import com.rappistream.orderprocessor.domain.model.Pedido;

/**
 * Interfaz del PatrÃ³n State para el ciclo de vida del Pedido.
 *
 * POO â€” PatrÃ³n State:
 * Cada estado concreto implementa esta interfaz y define
 * exactamente quÃ© transiciones son vÃ¡lidas desde Ã©l.
 * La entidad Pedido delega cada operaciÃ³n a su estado actual,
 * eliminando por completo los if-else o switch anidados.
 *
 * Principio Abierto/Cerrado (OCP):
 * Para agregar un nuevo estado basta con crear una nueva clase
 * que implemente esta interfaz. No hay que modificar Pedido.
 *
 * Principio de SustituciÃ³n de Liskov (LSP):
 * Cualquier implementaciÃ³n de OrderState puede ser asignada
 * a Pedido.estadoActual sin romper el comportamiento esperado.
 *
 * Transiciones vÃ¡lidas:
 *   CREADO â”€â”€â–º ASIGNADO (asignarRepartidor)
 *   ASIGNADO â”€â”€â–º EN_CAMINO (iniciarEntrega)
 *   EN_CAMINO â”€â”€â–º LLEGANDO (registrarLlegada)
 *   LLEGANDO â”€â”€â–º ENTREGADO (completarEntrega)
 *   ENTREGADO â”€â”€â–º [fin â€” estado terminal]
 *
 * Las transiciones invÃ¡lidas lanzan IllegalStateException
 * con un mensaje descriptivo (Fail-Fast).
 */
public interface OrderState {

    /**
     * Asigna un repartidor al pedido.
     * Solo vÃ¡lido desde el estado CREADO.
     */
    void asignarRepartidor(Pedido pedido, String driverId, String driverName);

    /**
     * Marca que el repartidor iniciÃ³ el recorrido.
     * Solo vÃ¡lido desde el estado ASIGNADO.
     */
    void iniciarEntrega(Pedido pedido);

    /**
     * Registra que el repartidor estÃ¡ llegando al destino.
     * Solo vÃ¡lido desde el estado EN_CAMINO.
     */
    void registrarLlegada(Pedido pedido);

    /**
     * Cierra el ciclo: el pedido fue entregado.
     * Solo vÃ¡lido desde el estado LLEGANDO.
     */
    void completarEntrega(Pedido pedido);

    /**
     * Nombre legible del estado actual para logs y persistencia.
     */
    String nombre();
}

