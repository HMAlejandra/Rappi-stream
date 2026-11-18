package com.rappistream.pushworker.exception;

/**
 * Excepción base para errores del push_notification_worker.
 *
 * Jerarquía:
 *   RuntimeException
 *   └── PushWorkerException ← abstract base
 *       └── PushDeliveryException ← fallo al enviar notificación Push
 */
public abstract class PushWorkerException extends RuntimeException {
    protected PushWorkerException(String mensaje) { super(mensaje); }
}
