package com.rappistream.orderapi.service.burst;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Estrategia de emisión a ritmo constante con concurrencia acotada.
 *
 * Crea un pool fijo de tamaño = concurrency (acotado por el invariante de
 * BurstOrden), publica los N pedidos y cierra el pool al terminar. Respeta
 * los límites de recursos: hilos controlados y espera máxima de 60s.
 */
@Component
public class FixedRateBurstStrategy implements BurstEmissionStrategy {

    private static final Logger log = LoggerFactory.getLogger(FixedRateBurstStrategy.class);
    private static final long ESPERA_FIN_MS = 60_000L;

    @Override
    public void ejecutar(BurstOrden orden, BurstOrderPublisherPort publisher) {
        ExecutorService pool = Executors.newFixedThreadPool(orden.concurrency());
        AtomicInteger publicados = new AtomicInteger(0);
        try {
            for (int i = 0; i < orden.objetivo(); i++) {
                pool.submit(() -> emitirUno(orden, publisher, publicados));
            }
            pool.shutdown();
            if (!pool.awaitTermination(ESPERA_FIN_MS, TimeUnit.MILLISECONDS)) {
                log.warn("[burst] Timeout esperando emisión — burstId={}", orden.burstId());
            }
            log.info("[burst] Ráfaga emitida — burstId={} publicados={}/{}",
                    orden.burstId(), publicados.get(), orden.objetivo());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[burst] Emisión interrumpida — burstId={}", orden.burstId());
        } finally {
            pool.shutdownNow();
        }
    }

    private void emitirUno(BurstOrden orden, BurstOrderPublisherPort publisher,
                          AtomicInteger publicados) {
        if (orden.rhythmMillis() > 0) {
            try { Thread.sleep(orden.rhythmMillis()); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        var req = orden.siguiente();
        if (req == null) return;
        publisher.publicarComando(req);
        publicados.incrementAndGet();
    }
}
