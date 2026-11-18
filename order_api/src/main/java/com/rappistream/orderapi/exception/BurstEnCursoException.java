package com.rappistream.orderapi.exception;

/**
 * Se lanza cuando se intenta disparar una ráfaga mientras otra está en curso.
 *
 * Mapea a HTTP 409 Conflict en GlobalExceptionHandler, evitando solapar
 * ráfagas y proteger los recursos de la instancia (una sola activa).
 */
public class BurstEnCursoException extends OrderApiException {

    public BurstEnCursoException(String mensaje) {
        super(mensaje);
    }
}
