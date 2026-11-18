package com.rappistream.orderapi.controller;

import com.rappistream.orderapi.dto.request.CreateOrderRequest;
import com.rappistream.orderapi.dto.response.OrderAcceptedResponse;
import com.rappistream.orderapi.service.OrderPublisherService;
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
    public ResponseEntity<OrderAcceptedResponse> crearPedido(
            @Valid @RequestBody CreateOrderRequest request) {

        String orderId = publisherService.publicarComando(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new OrderAcceptedResponse(orderId, "ACEPTADO",
                        "Pedido recibido y en cola de procesamiento"));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "order_api"));
    }
}
