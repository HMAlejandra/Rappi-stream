package com.rappistream.dashboardbackend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Proxea GET /api/orders/snapshot → dashboard_aggregator /api/snapshots.
 * El browser llama a este endpoint para hidratar el estado inicial.
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
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON).body(response.getBody());
        } catch (RestClientException e) {
            log.warn("[dashboard_backend] No se pudo obtener snapshot: {}", e.getMessage());
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("[]");
        }
    }
}
