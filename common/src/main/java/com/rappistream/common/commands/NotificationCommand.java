package com.rappistream.common.commands;

import com.rappistream.common.enums.NotificationType;

import java.time.Instant;
import java.util.Objects;

/**
 * Comando inmutable que representa una instrucción de notificación.
 *
 * POO — Inmutabilidad y Value Objects:
 * Record inmutable. No tiene setters. Una vez creado, no cambia.
 *
 * Flujo:
 * notification_router → serializa a JSON → RabbitMQ (exchange: notifications)
 * sms_worker          → deserializa → simula SMS
 * push_notification_worker → deserializa → simula Push
 *
 * RabbitMQ Topic Exchange:
 * El campo 'type' determina la routing key:
 * - SMS  → "notify.client.sms"
 * - PUSH → "notify.driver.push"
 */
public record NotificationCommand(

        /** Identificador único de la notificación (UUID). */
        String notificationId,

        /** Identificador del pedido relacionado. */
        String orderId,

        /**
         * Identificador del destinatario.
         * Para SMS: customerId o nombre del cliente.
         * Para PUSH: driverId.
         */
        String recipientId,

        /** Canal de envío: SMS para el cliente, PUSH para el repartidor. */
        NotificationType type,

        /** Texto del mensaje a mostrar/enviar. */
        String message,

        /** Momento en que se emitió el comando (UTC). */
        Instant issuedAt

) {

    /** Constructor compacto — Validación Fail-Fast. */
    public NotificationCommand {
        Objects.requireNonNull(notificationId, "notificationId no puede ser nulo");
        Objects.requireNonNull(orderId, "orderId no puede ser nulo");
        Objects.requireNonNull(recipientId, "recipientId no puede ser nulo");
        Objects.requireNonNull(type, "type no puede ser nulo");
        Objects.requireNonNull(issuedAt, "issuedAt no puede ser nulo");

        if (notificationId.isBlank()) {
            throw new IllegalArgumentException("notificationId no puede estar vacío");
        }
        if (orderId.isBlank()) {
            throw new IllegalArgumentException("orderId no puede estar vacío");
        }
        if (recipientId.isBlank()) {
            throw new IllegalArgumentException("recipientId no puede estar vacío");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message no puede ser nulo o vacío");
        }
    }
}
