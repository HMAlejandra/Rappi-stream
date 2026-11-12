package com.rappistream.orderprocessor.domain.state;

import com.rappistream.orderprocessor.domain.model.Pedido;

/**
 * Interfaz del Patrón State para el ciclo de vida del Pedido.
 *
 * POO — Patrón State:
 * Cada estado concreto implementa esta interfaz y define
 * exactamente qué transiciones son válidas desde él.
 * La entidad Pedido delega cada operación a su estado actual,
 * eliminando por completo los if-else o switch anidados.
 *
 * Principio Abierto/Cerrado (OCP):
 * Para agregar un nuevo estado basta con crear una nueva clase
 * que implemente esta interfaz. No hay que modificar Pedido.
 *
 * Principio de Sustitución de Liskov (LSP):
 * Cualquier implementación de OrderState puede ser asignada
 * a Pedido.estadoActual sin romper el comportamiento esperado.
 *
 * Transiciones válidas:
 *   CREADO ──► ASIGNADO (asignarRepartidor)
 *   ASIGNADO ──► EN_CAMINO (iniciarEntrega)
 *   EN_CAMINO ──► LLEGANDO (registrarLlegada)
 *   LLEGANDO ──► ENTREGADO (completarEntrega)
 *   ENTREGADO ──► [fin — estado terminal]
 *
 * Las transiciones inválidas lanzan IllegalStateException
 * con un mensaje descriptivo (Fail-Fast).
 */
public interface OrderState {

    /**
     * Asigna un repartidor al pedido.
     * Solo válido desde el estado CREADO.
     */
    void asignarRepartidor(Pedido pedido, String driverId, String driverName);

    /**
     * Marca que el repartidor inició el recorrido.
     * Solo válido desde el estado ASIGNADO.
     */
    void iniciarEntrega(Pedido pedido);

    /**
     * Registra que el repartidor está llegando al destino.
     * Solo válido desde el estado EN_CAMINO.
     */
    void registrarLlegada(Pedido pedido);

    /**
     * Cierra el ciclo: el pedido fue entregado.
     * Solo válido desde el estado LLEGANDO.
     */
    void completarEntrega(Pedido pedido);

    /**
     * Nombre legible del estado actual para logs y persistencia.
     */
    String nombre();
}
