package com.rappistream.pushworker.service;

import com.rappistream.common.commands.NotificationCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Escucha driver_push_queue y simula el envío de Push al repartidor.
 *
 * Escalabilidad horizontal: múltiples réplicas comparten la cola.
 * RabbitMQ distribuye en round-robin entre ellas.
 *
 * En producción real: llamar a FCM / APNs aquí.
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
