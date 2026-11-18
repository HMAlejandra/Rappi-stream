package com.rappistream.orderapi.exception;

/**
 * Excepción base para errores del API de pedidos.
 *
 * Jerarquía:
 *   RuntimeException
 *   └── OrderApiException  ← abstract base
 *       └── InvalidOrderRequestException ← validación de negocio
 */
public abstract class OrderApiException extends RuntimeException {

    protected OrderApiException(String mensaje) {
        super(mensaje);
    }
}
