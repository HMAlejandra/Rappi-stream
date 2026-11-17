package com.rappistream.orderapi.infrastructure.web;

import com.rappistream.orderapi.application.OrderPublisherService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlador REST para el recurso /api/orders.
 *
 * SRP: solo gestiona la capa HTTP.
 * No contiene lógica de negocio ni detalles de mensajería.
 *
 * Respuesta exitosa: 202 Accepted
 * El pedido fue aceptado y encolado — no procesado aún.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderPublisherService publisherService;

    public OrderController(OrderPublisherService publisherService) {
        this.publisherService = publisherService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> crearPedido(
            @Valid @RequestBody CreateOrderRequest request) {

        String orderId = publisherService.publicarComando(request);

        Map<String, String> response = Map.of(
                "orderId", orderId,
                "status",  "ACEPTADO",
                "message", "Pedido recibido y en cola de procesamiento"
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "order_api"));
    }
}
