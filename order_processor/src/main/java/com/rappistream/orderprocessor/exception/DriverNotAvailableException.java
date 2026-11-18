package com.rappistream.orderprocessor.exception;

/**
 * Se lanza cuando no hay repartidores disponibles para un pedido.
 */
public class DriverNotAvailableException extends OrderProcessingException {

    public DriverNotAvailableException(String orderId) {
        super("No hay repartidores disponibles para asignar al pedido: " + orderId);
    }
}
