package com.rappistream.dashboardbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappistream.common.dto.BurstTriggeredDTO;
import com.rappistream.dashboardbackend.service.BurstMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

/**
 * Proxy del Modo Ráfaga: reenvía la solicitud a order_api e inicia la sesión
 * de métricas en vivo. SRP: solo HTTP + orquestación de la sesión de métricas.
 */
@RestController
@RequestMapping("/api/orders")
public class BurstProxyController {

    private static final Logger log = LoggerFactory.getLogger(BurstProxyController.class);
    @Value("${order.api.url}")
    private String orderApiUrl;
    private final RestTemplate restTemplate = new RestTemplate();
    private final BurstMetricsService burstMetricsService;
    private final ObjectMapper objectMapper;

    public BurstProxyController(BurstMetricsService burstMetricsService, ObjectMapper objectMapper) {
        this.burstMetricsService = burstMetricsService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/burst", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> disparar(@RequestBody String bodyRaw) {
        String url = orderApiUrl + "/api/orders/burst";
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>(bodyRaw, h), String.class);
            iniciarSesionSiOk(resp);
            return ResponseEntity.status(resp.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON).body(resp.getBody());
        } catch (Exception e) {
            log.error("[dashboard_backend] Error proxy ráfaga: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"error\":\"No se pudo conectar con order_api\"}");
        }
    }

    @PostMapping("/burst/reset")
    public ResponseEntity<Void> reiniciar() {
        burstMetricsService.reiniciar();
        return ResponseEntity.ok().build();
    }

    private void iniciarSesionSiOk(ResponseEntity<String> resp) {
        if (resp.getBody() == null) return;
        try {
            BurstTriggeredDTO dto = objectMapper.readValue(resp.getBody(), BurstTriggeredDTO.class);
            Instant inicio = dto.startedAt() != null ? dto.startedAt() : Instant.now();
            burstMetricsService.iniciarSesion(dto.burstId(), dto.emitted(), inicio);
        } catch (Exception e) {
            log.warn("[dashboard_backend] No se pudo iniciar sesión de métricas: {}", e.getMessage());
        }
    }
}
