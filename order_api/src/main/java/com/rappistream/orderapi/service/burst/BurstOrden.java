package com.rappistream.orderapi.service.burst;

import com.rappistream.orderapi.dto.request.CreateOrderRequest;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Aggregate de la ráfaga: encapsula el progreso y genera cada pedido.
 *
 * Clase enriquecida — protege el invariante emitidos <= objetivo y oculta
 * la generación de datos variados para evitar colisiones entre pedidos.
 * No expone setters: el estado solo muta a través de siguiente().
 */
public final class BurstOrden {

    private final String burstId;
    private final int objetivo;
    private final int concurrency;
    private final long rhythmMillis;
    private final Instant iniciadoEn;
    private final String baseName;
    private final String baseProduct;
    private final String baseAddress;
    private final AtomicInteger emitidos = new AtomicInteger(0);

    public BurstOrden(String burstId, int objetivo, int concurrency, long rhythmMillis,
                      String baseName, String baseProduct, String baseAddress) {
        if (burstId == null || burstId.isBlank())
            throw new IllegalArgumentException("burstId es obligatorio");
        if (objetivo < 1) throw new IllegalArgumentException("objetivo debe ser >= 1");
        if (concurrency < 1) throw new IllegalArgumentException("concurrency debe ser >= 1");
        this.burstId = burstId;
        this.objetivo = objetivo;
        this.concurrency = concurrency;
        this.rhythmMillis = rhythmMillis;
        this.iniciadoEn = Instant.now();
        this.baseName = baseName;
        this.baseProduct = baseProduct;
        this.baseAddress = baseAddress;
    }

    /** Genera el siguiente pedido o null si la ráfaga ya está completa. */
    public CreateOrderRequest siguiente() {
        int n = emitidos.incrementAndGet();
        if (n > objetivo) return null;
        return new CreateOrderRequest(
                baseName + " #" + n, baseProduct, 1, baseAddress + " #" + n);
    }

    public int emitidos() { return Math.min(emitidos.get(), objetivo); }
    public boolean estaCompleta() { return emitidos.get() >= objetivo; }
    public int objetivo() { return objetivo; }
    public int concurrency() { return concurrency; }
    public long rhythmMillis() { return rhythmMillis; }
    public String burstId() { return burstId; }
    public Instant iniciadoEn() { return iniciadoEn; }
}
