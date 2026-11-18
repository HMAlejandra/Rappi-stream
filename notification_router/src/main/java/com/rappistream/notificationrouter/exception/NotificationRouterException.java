package com.rappistream.notificationrouter.exception;

/**
 * Excepción base para errores del enrutador de notificaciones.
 *
 * Jerarquía:
 *   RuntimeException
 *   └── NotificationRouterException ← abstract base
 *       └── NotificationBuildException ← error construyendo una notificación
 */
public abstract class NotificationRouterException extends RuntimeException {

    protected NotificationRouterException(String mensaje) {
        super(mensaje);
    }
}
