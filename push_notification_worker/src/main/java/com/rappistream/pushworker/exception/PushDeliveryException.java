package com.rappistream.pushworker.exception;

/**
 * Se lanza cuando falla el envío de una notificación Push (FCM no disponible, etc.).
 */
public class PushDeliveryException extends PushWorkerException {

    public PushDeliveryException(String recipientId, Throwable causa) {
        super("Error enviando Push al repartidor '" + recipientId + "': " + causa.getMessage());
    }
}
