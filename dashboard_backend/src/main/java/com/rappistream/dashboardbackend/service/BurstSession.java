package com.rappistream.dashboardbackend.service;

import com.rappistream.common.dto.BurstMetricsDTO;
import com.rappistream.common.enums.BurstStatus;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Aggregate de la sesión de una ráfaga: cuenta pedidos procesados en vivo.
 *
 * Clase enriquecida — encapsula progreso, throughput y finalización. El
 * conteo se alimenta de los OrderStatusDTO reales que llegan desde
 * dashboard_aggregator (Kafka order_events), no de un temporizador.
 */
public final class BurstSession {

    private final String burstId;
    private final int emitidos;
    private final Instant iniciadoEn;
    private final AtomicInteger procesados = new AtomicInteger(0);
    private final Set<String> vistos = ConcurrentHashMap.newKeySet();
    private volatile BurstStatus estado = BurstStatus.RUNNING;

    public BurstSession(String burstId, int emitidos, Instant iniciadoEn) {
        if (burstId == null || burstId.isBlank())
            throw new IllegalArgumentException("burstId es obligatorio");
        if (emitidos < 1) throw new IllegalArgumentException("emitidos debe ser >= 1");
        if (iniciadoEn == null) throw new IllegalArgumentException("iniciadoEn es obligatorio");
        this.burstId = burstId;
        this.emitidos = emitidos;
        this.iniciadoEn = iniciadoEn;
    }

    /** Cuenta un pedido solo la primera vez que se ve. Devuelve true si fue nuevo. */
    public boolean registrar(String orderId) {
        if (orderId == null || !vistos.add(orderId)) return false;
        procesados.incrementAndGet();
        if (procesados.get() >= emitidos) estado = BurstStatus.COMPLETED;
        return true;
    }

    public BurstMetricsDTO toDTO() {
        long elapsed = Math.max(1, Instant.now().toEpochMilli() - iniciadoEn.toEpochMilli());
        double throughput = procesados.get() * 1000.0 / elapsed;
        return new BurstMetricsDTO(burstId, emitidos, procesados.get(),
                throughput, elapsed, estado, iniciadoEn, Instant.now());
    }

    public boolean estaCompletada() { return estado.esTerminal(); }
    public String burstId() { return burstId; }
    public int emitidos() { return emitidos; }
    public int procesados() { return procesados.get(); }
}
