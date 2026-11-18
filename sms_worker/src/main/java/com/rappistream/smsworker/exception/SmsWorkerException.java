package com.rappistream.smsworker.exception;

/**
 * Excepción base para errores del sms_worker.
 *
 * Jerarquía:
 *   RuntimeException
 *   └── SmsWorkerException ← abstract base
 *       └── SmsDeliveryException ← fallo al enviar SMS
 */
public abstract class SmsWorkerException extends RuntimeException {
    protected SmsWorkerException(String mensaje) { super(mensaje); }
}
