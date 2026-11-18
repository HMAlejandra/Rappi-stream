package com.rappistream.orderprocessor.exception;

/**
 * Excepción base para errores del procesamiento de pedidos.
 *
 * Jerarquía:
 *   RuntimeException
 *   └── OrderProcessingException  ← abstract base
 *       └── DriverNotAvailableException ← sin repartidores disponibles
 */
public abstract class OrderProcessingException extends RuntimeException {

    protected OrderProcessingException(String mensaje) {
        super(mensaje);
    }
}
