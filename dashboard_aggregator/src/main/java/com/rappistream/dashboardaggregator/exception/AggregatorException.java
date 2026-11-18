package com.rappistream.dashboardaggregator.exception;

/**
 * Excepción base para errores del dashboard_aggregator.
 *
 * Jerarquía:
 *   RuntimeException
 *   └── AggregatorException ← abstract base
 *       └── DuplicateEventException ← evento ya procesado (idempotencia)
 */
public abstract class AggregatorException extends RuntimeException {

    protected AggregatorException(String mensaje) {
        super(mensaje);
    }
}
