package com.rappistream.dashboardaggregator.exception;

/**
 * Se lanza cuando se intenta procesar un evento que ya fue registrado.
 * El aggregator la captura silenciosamente para garantizar idempotencia.
 */
public class DuplicateEventException extends AggregatorException {

    public DuplicateEventException(String eventId) {
        super("El evento '" + eventId + "' ya fue procesado anteriormente.");
    }
}
