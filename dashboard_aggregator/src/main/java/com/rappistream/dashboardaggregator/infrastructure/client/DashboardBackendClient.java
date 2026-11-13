package com.rappistream.dashboardaggregator.infrastructure.client;

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
 * Fix aplicado:
 * 1. RestTemplate configurado con el ObjectMapper que incluye JavaTimeModule
 *    → serializa Instant como "2026-11-13T03:27:44Z" (ISO-8601), no como array
 * 2. Content-Type: application/json explícito en cada request
 *    → el backend puede deserializar el body correctamente
 * 3. URL usa nombre de servicio Docker sin guión bajo gracias a la
 *    propiedad server.tomcat.relaxed-path-chars en el backend
 */
@Component
public class DashboardBackendClient {

    private static final Logger log = LoggerFactory.getLogger(DashboardBackendClient.class);

    private final RestTemplate restTemplate;
    private final String       backendUrl;

    public DashboardBackendClient(
            @Value("${dashboard.backend.url:http://dashboard_backend:8087}") String backendUrl,
            ObjectMapper objectMapper) {

        // Configurar RestTemplate con el mismo ObjectMapper del aggregator
        // (que incluye JavaTimeModule → Instant como ISO-8601)
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(objectMapper);

        this.restTemplate = new RestTemplate(List.of(converter));
        this.backendUrl   = backendUrl;
    }

    /**
     * Envía el estado actualizado al dashboard_backend para que
     * haga broadcast por WebSocket a todos los navegadores conectados.
     */
    public void notificarActualizacion(OrderStatusDTO dto) {
        try {
            String url = backendUrl + "/internal/update";

            // Especificar Content-Type explícitamente para que Spring
            // seleccione el converter correcto en el backend
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<OrderStatusDTO> request = new HttpEntity<>(dto, headers);

            ResponseEntity<Void> response = restTemplate.postForEntity(url, request, Void.class);
            log.debug("[dashboard_aggregator] Notificación enviada — orderId={} status={}",
                    dto.orderId(), response.getStatusCode());

        } catch (RestClientException e) {
            log.warn("[dashboard_aggregator] No se pudo notificar al dashboard_backend — orderId={}: {}",
                    dto.orderId(), e.getMessage());
        }
    }
}
