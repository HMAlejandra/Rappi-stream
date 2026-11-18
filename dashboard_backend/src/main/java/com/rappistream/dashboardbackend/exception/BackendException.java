package com.rappistream.dashboardbackend.exception;

/**
 * Excepción base para errores del dashboard_backend.
 *
 * Jerarquía:
 *   RuntimeException
 *   └── BackendException ← abstract base
 *       └── ProxyConnectionException ← fallo al conectar con un microservicio upstream
 */
public abstract class BackendException extends RuntimeException {
    protected BackendException(String mensaje) { super(mensaje); }
}
