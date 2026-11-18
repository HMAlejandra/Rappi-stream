package com.rappistream.dashboardbackend.service;

import com.rappistream.common.commands.WorkerActivitySignal;
import com.rappistream.common.dto.WorkerActivityDTO;
import com.rappistream.common.enums.ReplicaStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Consume señales de telemetría reales de los workers (worker_telemetry_queue)
 * y alimenta el semáforo del dashboard. Hace broadcast del snapshot completo
 * a /topic/workers en cada señal, de modo que cada luz refleja trabajo real.
 */
@Component
public class WorkerTelemetryListener {

    private static final Logger log = LoggerFactory.getLogger(WorkerTelemetryListener.class);
    private final SimpMessagingTemplate ws;
    private final ReplicaRegistry registry;
    private final ReplicaStatusEvaluator evaluator;

    public WorkerTelemetryListener(SimpMessagingTemplate ws, ReplicaRegistry registry,
                                   ReplicaStatusEvaluator evaluator) {
        this.ws = ws;
        this.registry = registry;
        this.evaluator = evaluator;
    }

    @RabbitListener(queues = "worker_telemetry_queue")
    public void onTelemetry(WorkerActivitySignal signal) {
        long lat = signal.latencyMillis();
        ReplicaStatus estado = evaluator.evaluar(lat, 0L);
        WorkerActivityDTO dto = new WorkerActivityDTO(signal.replicaId(), signal.workerType(),
                signal.processedCount(), estado, lat, Instant.now());
        registry.registrar(dto);
        ws.convertAndSend("/topic/workers", registry.snapshot());
        log.debug("[dashboard_backend] Telemetría — replica={} estado={} procesados={}",
                signal.replicaId(), estado, signal.processedCount());
    }
}
