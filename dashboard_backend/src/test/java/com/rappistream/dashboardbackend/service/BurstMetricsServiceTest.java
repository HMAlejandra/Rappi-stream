package com.rappistream.dashboardbackend.service;

import com.rappistream.common.dto.BurstMetricsDTO;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.mockito.Mockito.*;

/**
 * Pruebas de BurstMetricsService con SimpMessagingTemplate mock (AAA).
 */
class BurstMetricsServiceTest {

    @Test
    void iniciaSesionYHaceBroadcastInicial() {
        // Arrange
        SimpMessagingTemplate ws = mock(SimpMessagingTemplate.class);
        BurstMetricsService svc = new BurstMetricsService(ws);

        // Act
        svc.iniciarSesion("b1", 2, Instant.now());

        // Assert
        verify(ws).convertAndSend(eq("/topic/burst"), any(BurstMetricsDTO.class));
    }

    @Test
    void cuentaPedidosYEmiteBroadcastAlCompletar() {
        // Arrange
        SimpMessagingTemplate ws = mock(SimpMessagingTemplate.class);
        BurstMetricsService svc = new BurstMetricsService(ws);
        svc.iniciarSesion("b2", 2, Instant.now());
        reset(ws);

        // Act
        svc.onPedidoProcesado("o1");
        svc.onPedidoProcesado("o2");

        // Assert
        verify(ws, atLeastOnce()).convertAndSend(eq("/topic/burst"), any(BurstMetricsDTO.class));
    }

    @Test
    void noHaceNadaSinSesionActiva() {
        // Arrange
        SimpMessagingTemplate ws = mock(SimpMessagingTemplate.class);
        BurstMetricsService svc = new BurstMetricsService(ws);

        // Act
        svc.onPedidoProcesado("o1");

        // Assert
        verifyNoInteractions(ws);
    }

    @Test
    void reiniciarEmiteMetricasEnCero() {
        // Arrange
        SimpMessagingTemplate ws = mock(SimpMessagingTemplate.class);
        BurstMetricsService svc = new BurstMetricsService(ws);

        // Act
        svc.reiniciar();

        // Assert
        verify(ws).convertAndSend(eq("/topic/burst"), any(BurstMetricsDTO.class));
    }
}
