package com.rappistream.smsworker.infrastructure;

import com.rappistream.common.commands.NotificationCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listener de RabbitMQ para client_sms_queue — infraestructura.
 *
 * Simula el envío de SMS al cliente con un log estructurado.
 * En producción real, aquí se llamaría a Twilio / AWS SNS.
 *
 * Escalabilidad horizontal:
 * Múltiples réplicas comparten client_sms_queue.
 * RabbitMQ distribuye en round-robin entre ellas.
 */
@Component
public class SmsNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationListener.class);

    @RabbitListener(queues = "client_sms_queue")
    public void onSmsNotification(NotificationCommand comando) {
        log.info("📱 [sms_worker] SMS ENVIADO");
        log.info("   orderId    : {}", comando.orderId());
        log.info("   destinatario: {}", comando.recipientId());
        log.info("   mensaje    : {}", comando.message());
        log.info("   timestamp  : {}", comando.issuedAt());
    }
}
