package com.rappistream.pushworker.listener;

import com.rappistream.common.commands.NotificationCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listener de RabbitMQ para driver_push_queue.
 *
 * Simula el envío de notificación Push al repartidor con un log.
 * En producción real, aquí se llamaría a FCM (Firebase Cloud Messaging)
 * o APNs con el device token del repartidor.
 *
 * Escalabilidad horizontal:
 * Múltiples réplicas de push_notification_worker comparten driver_push_queue.
 * RabbitMQ distribuye en round-robin, demostrando balanceo de carga.
 */
@Component
public class PushNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationListener.class);

    @RabbitListener(queues = "driver_push_queue")
    public void onPushNotification(NotificationCommand comando) {
        log.info("🔔 [push_worker] PUSH ENVIADO");
        log.info("   orderId    : {}", comando.orderId());
        log.info("   repartidor : {}", comando.recipientId());
        log.info("   mensaje    : {}", comando.message());
        log.info("   timestamp  : {}", comando.issuedAt());
    }
}
