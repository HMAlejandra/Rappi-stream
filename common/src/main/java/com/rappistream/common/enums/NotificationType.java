package com.rappistream.common.enums;

/**
 * Canal de notificación destino.
 *
 * POO — Patrón Strategy:
 * notification_router usa este tipo para seleccionar
 * la estrategia correcta de construcción del comando
 * y la routing key de RabbitMQ correspondiente.
 *
 * RabbitMQ Topic Exchange:
 * SMS  → routing key: notify.client.sms  → cola: client_sms_queue
 * PUSH → routing key: notify.driver.push → cola: driver_push_queue
 */
public enum NotificationType {
    SMS,
    PUSH
}
