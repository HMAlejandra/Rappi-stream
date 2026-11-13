package com.rappistream.dashboardbackend.controller;

import com.rappistream.common.dto.OrderStatusDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint interno que recibe actualizaciones del dashboard_aggregator
 * y las reenvía a todos los clientes WebSocket conectados.
 *
 * SRP: solo recibe actualizaciones y hace broadcast WebSocket.
 * No tiene lógica de estado — esa vive en el aggregator.
 *
 * SimpMessagingTemplate: template de Spring para enviar mensajes
 * STOMP a destinos de forma programática.
 * convertAndSend("/topic/orders", dto) → el broker simple envía
 * el mensaje a todos los clientes suscritos a /topic/orders.
 */
@RestController
@RequestMapping("/internal")
public class InternalUpdateController {

    private static final Logger log = LoggerFactory.getLogger(InternalUpdateController.class);

    private final SimpMessagingTemplate messagingTemplate;

    public InternalUpdateController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Recibe el estado actualizado de un pedido y hace WebSocket broadcast.
     *
     * POST /internal/update
     * Body: OrderStatusDTO (JSON)
     * Response: 200 OK
     */
    @PostMapping("/update")
    public ResponseEntity<Void> recibirActualizacion(@RequestBody OrderStatusDTO dto) {
        log.info("[dashboard_backend] Broadcast WebSocket — orderId={} status={}",
                dto.orderId(), dto.status());

        // Enviar a TODOS los clientes suscritos a /topic/orders
        messagingTemplate.convertAndSend("/topic/orders", dto);

        return ResponseEntity.ok().build();
    }
}
