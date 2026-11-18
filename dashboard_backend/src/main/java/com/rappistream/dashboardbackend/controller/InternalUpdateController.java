package com.rappistream.dashboardbackend.controller;

import com.rappistream.common.dto.OrderStatusDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * Recibe actualizaciones del dashboard_aggregator y hace broadcast WebSocket.
 *
 * SRP: solo recibe y retransmite. No tiene lógica de estado.
 */
@RestController
@RequestMapping("/internal")
public class InternalUpdateController {

    private static final Logger log = LoggerFactory.getLogger(InternalUpdateController.class);

    private final SimpMessagingTemplate messagingTemplate;

    public InternalUpdateController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping("/update")
    public ResponseEntity<Void> recibirActualizacion(@RequestBody OrderStatusDTO dto) {
        log.info("[dashboard_backend] Broadcast WebSocket — orderId={} status={}",
                dto.orderId(), dto.status());
        messagingTemplate.convertAndSend("/topic/orders", dto);
        return ResponseEntity.ok().build();
    }
}
