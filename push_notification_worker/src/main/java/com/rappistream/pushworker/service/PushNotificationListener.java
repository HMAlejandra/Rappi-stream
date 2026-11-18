package com.rappistream.pushworker.service;

import com.rappistream.common.commands.NotificationCommand;
import com.rappistream.common.enums.WorkerType;
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
 * Semáforo de réplicas: mide la latencia del procesamiento y reporta la
 * actividad de esta réplica hacia el dashboard vía WorkerActivityRecorder.
 *
 * En producción real: llamar a FCM / APNs aquí.
 */
@Component
public class PushNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationListener.class);

    private final WorkerActivityRecorder recorder;

    public PushNotificationListener(WorkerActivityRecorder recorder) {
        this.recorder = recorder;
    }

    @RabbitListener(queues = "driver_push_queue")
    public void onPushNotification(NotificationCommand comando) {
        long inicio = System.nanoTime();
        log.info("🔔 [push_worker] PUSH ENVIADO — orderId={} repartidor={}",
                comando.orderId(), comando.recipientId());
        log.info("   mensaje: {}", comando.message());
        long latencyMs = Math.max(1, (System.nanoTime() - inicio) / 1_000_000);
        recorder.registrar(WorkerType.PUSH, latencyMs);
    }
}
