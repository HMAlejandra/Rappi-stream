package com.rappistream.dashboardbackend.controller;

import com.rappistream.common.dto.OrderStatusDTO;
import com.rappistream.dashboardbackend.service.BurstMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * Recibe actualizaciones del dashboard_aggregator y hace broadcast WebSocket.
 *
 * Además alimenta el servicio de métricas de ráfaga con cada pedido que ingresa
 * al sistema (flujo real Kafka → aggregator → aquí), de modo que las métricas
 * en vivo reflejan trabajo real y no un temporizador simulado.
 */
@RestController
@RequestMapping("/internal")
public class InternalUpdateController {

    private static final Logger log = LoggerFactory.getLogger(InternalUpdateController.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final BurstMetricsService burstMetricsService;

    public InternalUpdateController(SimpMessagingTemplate messagingTemplate,
                                    BurstMetricsService burstMetricsService) {
        this.messagingTemplate = messagingTemplate;
        this.burstMetricsService = burstMetricsService;
    }

    @PostMapping("/update")
    public ResponseEntity<Void> recibirActualizacion(@RequestBody OrderStatusDTO dto) {
        log.info("[dashboard_backend] Broadcast WebSocket — orderId={} status={}",
                dto.orderId(), dto.status());
        messagingTemplate.convertAndSend("/topic/orders", dto);
        burstMetricsService.onPedidoProcesado(dto.orderId());
        return ResponseEntity.ok().build();
    }
}
