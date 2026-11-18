package com.rappistream.orderapi.service.burst;

import com.rappistream.common.dto.BurstTriggeredDTO;
import com.rappistream.orderapi.dto.request.BurstRequest;
import com.rappistream.orderapi.exception.BurstEnCursoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orquesta el disparo de una ráfaga de forma asíncrona y acotada.
 *
 * DIP: depende del puerto de publicación y de la estrategia de emisión
 * (ambos inyectados por constructor). Recurso controlado: una sola ráfaga
 * activa por instancia y un único hilo coordinador demonio.
 */
@Service
public class BurstOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(BurstOrchestrator.class);
    private final BurstOrderPublisherPort publisher;
    private final BurstEmissionStrategy strategy;
    private final ExecutorService coordinador = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "burst-coordinator");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean activa = new AtomicBoolean(false);

    public BurstOrchestrator(BurstOrderPublisherPort publisher, BurstEmissionStrategy strategy) {
        this.publisher = publisher;
        this.strategy = strategy;
    }

    public BurstTriggeredDTO disparar(BurstRequest request) {
        if (!activa.compareAndSet(false, true)) {
            throw new BurstEnCursoException("Ya hay una ráfaga en curso");
        }
        String burstId = java.util.UUID.randomUUID().toString();
        BurstOrden orden = new BurstOrden(burstId, request.count(), request.concurrency(),
                request.rhythmMillis(), request.baseName(), request.baseProduct(),
                request.baseAddress());
        coordinador.submit(() -> correr(orden));
        log.info("[burst] Ráfaga disparada — burstId={} objetivo={}", burstId, request.count());
        return new BurstTriggeredDTO(burstId, request.count(), request.concurrency(),
                request.rhythmMillis(), orden.iniciadoEn(), "Ráfaga encolada asíncronamente");
    }

    private void correr(BurstOrden orden) {
        try {
            strategy.ejecutar(orden, publisher);
        } finally {
            activa.set(false);
        }
    }
}
