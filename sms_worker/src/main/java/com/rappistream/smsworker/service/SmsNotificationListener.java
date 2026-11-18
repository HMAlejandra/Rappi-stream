package com.rappistream.smsworker.service;

import com.rappistream.common.commands.NotificationCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Escucha client_sms_queue y simula el envío de SMS al cliente.
 *
 * Escalabilidad horizontal: múltiples réplicas comparten la cola.
 * RabbitMQ distribuye en round-robin entre ellas (Work Queue pattern).
 *
 * En producción real: llamar a Twilio / AWS SNS aquí.
 */
@Component
public class SmsNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationListener.class);

    @RabbitListener(queues = "client_sms_queue")
    public void onSmsNotification(NotificationCommand comando) {
        log.info("📱 [sms_worker] SMS ENVIADO");
        log.info("   orderId     : {}", comando.orderId());
        log.info("   destinatario: {}", comando.recipientId());
        log.info("   mensaje     : {}", comando.message());
        log.info("   timestamp   : {}", comando.issuedAt());
    }
}
