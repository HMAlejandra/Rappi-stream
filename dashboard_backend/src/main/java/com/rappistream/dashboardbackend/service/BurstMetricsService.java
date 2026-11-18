package com.rappistream.dashboardbackend.service;

import com.rappistream.common.dto.BurstMetricsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mantiene la sesión de ráfaga activa y publica métricas en vivo por WebSocket
 * a /topic/burst. DIP: depende de SimpMessagingTemplate. Recurso controlado:
 * una sola sesión activa y broadcast con throttle para no saturar el navegador.
 */
@Service
public class BurstMetricsService {

    private static final Logger log = LoggerFactory.getLogger(BurstMetricsService.class);
    private static final long MIN_ENTRE_BROADCAST_MS = 200L;
    private final SimpMessagingTemplate ws;
    private final AtomicReference<BurstSession> sesion = new AtomicReference<>();
    private volatile long ultimoBroadcast = 0L;

    public BurstMetricsService(SimpMessagingTemplate ws) {
        this.ws = ws;
    }

    public void iniciarSesion(String burstId, int emitidos, Instant iniciadoEn) {
        sesion.set(new BurstSession(burstId, emitidos, iniciadoEn));
        broadcast(true);
    }

    public void onPedidoProcesado(String orderId) {
        BurstSession s = sesion.get();
        if (s == null || s.estaCompletada()) return;
        if (s.registrar(orderId)) broadcast(s.estaCompletada());
    }

    public void reiniciar() {
        sesion.set(null);
        ws.convertAndSend("/topic/burst", new BurstMetricsDTO(
                null, 0, 0, 0d, 0L, null, null, Instant.now()));
    }

    private void broadcast(boolean forzar) {
        BurstSession s = sesion.get();
        if (s == null) return;
        long ahora = System.currentTimeMillis();
        if (!forzar && ahora - ultimoBroadcast < MIN_ENTRE_BROADCAST_MS) return;
        ultimoBroadcast = ahora;
        ws.convertAndSend("/topic/burst", s.toDTO());
    }
}
