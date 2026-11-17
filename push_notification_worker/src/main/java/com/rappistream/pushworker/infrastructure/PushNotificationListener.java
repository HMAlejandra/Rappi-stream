package com.rappistream.pushworker.infrastructure;

import com.rappistream.common.commands.NotificationCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listener de RabbitMQ para driver_push_queue — infraestructura.
 *
 * Simula el envío de notificación Push al repartidor con un log.
 * En producción real, aquí se llamaría a FCM / APNs.
 *
 * Escalabilidad horizontal:
 * Múltiples réplicas comparten driver_push_queue.
 * RabbitMQ distribuye en round-robin entre ellas.
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
