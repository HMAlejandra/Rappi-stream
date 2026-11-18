package com.rappistream.smsworker.service;

import com.rappistream.common.commands.NotificationCommand;
import com.rappistream.common.enums.WorkerType;
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
 * Semáforo de réplicas: mide la latencia del procesamiento y reporta la
 * actividad de esta réplica hacia el dashboard vía WorkerActivityRecorder.
 *
 * En producción real: llamar a Twilio / AWS SNS aquí.
 */
@Component
public class SmsNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationListener.class);

    private final WorkerActivityRecorder recorder;

    public SmsNotificationListener(WorkerActivityRecorder recorder) {
        this.recorder = recorder;
    }

    @RabbitListener(queues = "client_sms_queue")
    public void onSmsNotification(NotificationCommand comando) {
        long inicio = System.nanoTime();
        log.info("📱 [sms_worker] SMS ENVIADO — orderId={} destinatario={}",
                comando.orderId(), comando.recipientId());
        log.info("   mensaje: {}", comando.message());
        long latencyMs = Math.max(1, (System.nanoTime() - inicio) / 1_000_000);
        recorder.registrar(WorkerType.SMS, latencyMs);
    }
}
