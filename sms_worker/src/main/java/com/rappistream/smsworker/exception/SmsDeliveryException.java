package com.rappistream.smsworker.exception;

/**
 * Se lanza cuando falla el envío de un SMS (proveedor no disponible, etc.).
 */
public class SmsDeliveryException extends SmsWorkerException {

    public SmsDeliveryException(String recipientId, Throwable causa) {
        super("Error enviando SMS al destinatario '" + recipientId + "': " + causa.getMessage());
    }
}
