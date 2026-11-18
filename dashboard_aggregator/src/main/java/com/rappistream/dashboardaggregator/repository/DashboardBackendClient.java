package com.rappistream.dashboardaggregator.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappistream.common.dto.OrderStatusDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Cliente HTTP que notifica al dashboard_backend cuando hay
 * una actualización de estado de pedido.
 *
 * RestTemplate configurado con el mismo ObjectMapper del aggregator
 * para serializar Instant como ISO-8601 (no como array numérico).
 */
@Component
public class DashboardBackendClient {

    private static final Logger log = LoggerFactory.getLogger(DashboardBackendClient.class);

    private final RestTemplate restTemplate;
    private final String       backendUrl;

    public DashboardBackendClient(
            @Value("${dashboard.backend.url}") String backendUrl,
            ObjectMapper objectMapper) {
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(objectMapper);
        this.restTemplate = new RestTemplate(List.of(converter));
        this.backendUrl   = backendUrl;
    }

    public void notificarActualizacion(OrderStatusDTO dto) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<OrderStatusDTO> request = new HttpEntity<>(dto, headers);
            restTemplate.postForEntity(backendUrl + "/internal/update", request, Void.class);
            log.debug("[dashboard_aggregator] Notificación enviada — orderId={}", dto.orderId());
        } catch (RestClientException e) {
            log.warn("[dashboard_aggregator] No se pudo notificar al backend — orderId={}: {}",
                    dto.orderId(), e.getMessage());
        }
    }
}
