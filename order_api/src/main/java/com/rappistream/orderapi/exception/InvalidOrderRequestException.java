package com.rappistream.orderapi.exception;

/**
 * Se lanza cuando el request de creación de pedido viola reglas de negocio.
 * HTTP 400 Bad Request.
 */
public class InvalidOrderRequestException extends OrderApiException {

    public InvalidOrderRequestException(String mensaje) {
        super(mensaje);
    }
}
