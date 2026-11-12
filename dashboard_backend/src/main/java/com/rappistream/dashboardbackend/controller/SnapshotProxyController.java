package com.rappistream.dashboardbackend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Proxea GET /api/orders/snapshot → dashboard_aggregator /api/snapshots
 * El browser llama a este endpoint al conectarse para hidratar
 * el estado inicial sin esperar el próximo evento WebSocket.
 */
@RestController
@RequestMapping("/api/orders")
public class SnapshotProxyController {

    private static final Logger log = LoggerFactory.getLogger(SnapshotProxyController.class);

    @Value("${aggregator.url}")
    private String aggregatorUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/snapshot")
    public ResponseEntity<String> obtenerSnapshot() {
        String url = aggregatorUrl + "/api/snapshots";
        log.debug("[dashboard_backend] Proxy snapshot → GET {}", url);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (RestClientException e) {
            log.warn("[dashboard_backend] No se pudo obtener snapshot del aggregator: {}", e.getMessage());
            // Devolver array vacío — el frontend lo maneja bien
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("[]");
        }
    }
}
