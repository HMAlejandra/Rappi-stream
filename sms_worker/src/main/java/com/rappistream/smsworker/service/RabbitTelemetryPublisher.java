package com.rappistream.smsworker.service;

import com.rappistream.common.commands.WorkerActivitySignal;
import com.rappistream.smsworker.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica señales de telemetría en worker_telemetry_queue (RabbitMQ).
 *
 * Fire-and-forget tolerante a fallos: si el broker no está disponible, solo
 * se advierte en log para no degradar el procesamiento del SMS.
 */
@Component
public class RabbitTelemetryPublisher implements WorkerTelemetryPort {

    private static final Logger log = LoggerFactory.getLogger(RabbitTelemetryPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public RabbitTelemetryPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void reportar(WorkerActivitySignal signal) {
        try {
            rabbitTemplate.convertAndSend(RabbitMqConfig.WORKER_TELEMETRY_QUEUE, signal);
        } catch (Exception e) {
            log.warn("[sms_worker] No se pudo reportar telemetría: {}", e.getMessage());
        }
    }
}
