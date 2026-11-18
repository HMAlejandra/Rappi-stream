package com.rappistream.notificationrouter.exception;

/**
 * Se lanza cuando no se puede construir un NotificationCommand
 * a partir de un evento Kafka (payload malformado, tipo desconocido, etc.).
 */
public class NotificationBuildException extends NotificationRouterException {

    public NotificationBuildException(String eventType, String orderId, Throwable causa) {
        super(String.format("Error construyendo notificación para evento '%s' del pedido '%s': %s",
                eventType, orderId, causa.getMessage()));
    }
}
