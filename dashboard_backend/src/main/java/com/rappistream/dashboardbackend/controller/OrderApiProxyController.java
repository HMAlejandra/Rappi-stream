package com.rappistream.dashboardbackend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

/**
 * Proxy reverso: reenvía POST /api/orders al order_api como String raw.
 * Evita problemas de serialización de tipos (Integer vs String).
 */
@RestController
@RequestMapping("/api")
public class OrderApiProxyController {

    private static final Logger log = LoggerFactory.getLogger(OrderApiProxyController.class);

    @Value("${order.api.url}")
    private String orderApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping(value = "/orders", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> crearPedido(@RequestBody String bodyRaw) {
        String url = orderApiUrl + "/api/orders";
        log.info("[dashboard_backend] Proxy → POST {}", url);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(bodyRaw, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (Exception e) {
            log.error("[dashboard_backend] Error proxy → {}: {}", url, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"No se pudo conectar con order_api\"}");
        }
    }
}
