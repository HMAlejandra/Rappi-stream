package com.rappistream.dashboardbackend.service;

import com.rappistream.common.dto.WorkerActivityDTO;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro en memoria del estado de cada réplica de worker.
 *
 * Clase enriquecida — encapsula el map y reevalúa el semáforo en vivo sobre
 * el snapshot, de modo que las réplicas pasen a gris cuando dejan de reportar
 * (sin necesidad de un temporizador que "invente" actividad).
 */
@Component
public class ReplicaRegistry {

    private final ReplicaStatusEvaluator evaluator;
    private final ConcurrentHashMap<String, WorkerActivityDTO> replicas = new ConcurrentHashMap<>();

    public ReplicaRegistry(ReplicaStatusEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public void registrar(WorkerActivityDTO dto) {
        replicas.put(dto.replicaId(), dto);
    }

    public Collection<WorkerActivityDTO> snapshot() {
        Instant ahora = Instant.now();
        replicas.replaceAll((id, dto) -> reevaluar(dto, ahora));
        return replicas.values();
    }

    private WorkerActivityDTO reevaluar(WorkerActivityDTO dto, Instant ahora) {
        long desde = Math.max(0, ahora.toEpochMilli() - dto.lastSeenAt().toEpochMilli());
        var estado = evaluator.evaluar(dto.latencyMillis(), desde);
        return new WorkerActivityDTO(dto.replicaId(), dto.workerType(),
                dto.processedCount(), estado, dto.latencyMillis(), dto.lastSeenAt());
    }

    public void limpiar() {
        replicas.clear();
    }
}
