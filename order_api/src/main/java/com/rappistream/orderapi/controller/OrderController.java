package com.rappistream.orderapi.controller;

import com.rappistream.orderapi.dto.CreateOrderRequest;
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
 * @Valid delega la validación del body a Bean Validation
 * antes de que el método sea invocado. Si falla, Spring
 * responde automáticamente con 400 Bad Request.
 *
 * Respuesta exitosa: 202 Accepted
 * El pedido fue aceptado y encolado — no procesado aún.
 * 202 es más correcto que 201 Created porque el recurso
 * no está creado todavía (lo crea order_processor).
 *
 * @CrossOrigin:
 * El dashboard (puerto 8087) llama directamente a este endpoint
 * (puerto 8080). El navegador bloquea peticiones cross-origin
 * a menos que el servidor responda con los headers CORS correctos.
 * allowedOriginPatterns="*" permite cualquier origen en desarrollo.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderPublisherService publisherService;

    public OrderController(OrderPublisherService publisherService) {
        this.publisherService = publisherService;
    }

    /**
     * Acepta un nuevo pedido, lo encola en RabbitMQ y devuelve
     * el orderId generado para que el cliente pueda seguirlo.
     *
     * POST /api/orders
     * Body: { "customerName": "...", "product": "...", "quantity": 2, "address": "..." }
     *
     * Response 202:
     * { "orderId": "uuid", "status": "ACEPTADO", "message": "Pedido recibido y en cola de procesamiento" }
     */
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

    /**
     * Healthcheck mínimo.
     * GET /api/orders/health → 200 OK
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "order_api"));
    }
}
