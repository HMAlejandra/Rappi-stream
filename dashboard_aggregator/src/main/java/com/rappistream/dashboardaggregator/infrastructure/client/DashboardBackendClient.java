package com.rappistream.dashboardaggregator.infrastructure.client;

import com.rappistream.common.dto.OrderStatusDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Cliente HTTP que notifica al dashboard_backend cuando hay
 * una actualización de estado de pedido.
 *
 * DIP: el listener no sabe cómo se notifica al backend.
 * Solo conoce este componente por inyección.
 *
 * Resiliencia:
 * Si el dashboard_backend no está disponible, se loguea el error
 * pero NO se lanza excepción. El aggregator sigue procesando
 * eventos — la pérdida de una notificación WebSocket es aceptable
 * para el MVP (el cliente puede reconectarse y pedir el estado actual).
 *
 * RestTemplate es síncrono y suficiente para el MVP.
 * En producción se usaría WebClient (reactivo) o un message bus.
 */
@Component
public class DashboardBackendClient {

    private static final Logger log = LoggerFactory.getLogger(DashboardBackendClient.class);

    private final RestTemplate restTemplate;
    private final String       backendUrl;

    public DashboardBackendClient(
            @Value("${dashboard.backend.url}") String backendUrl) {
        this.restTemplate = new RestTemplate();
        this.backendUrl   = backendUrl;
    }

    /**
     * Envía el estado actualizado al dashboard_backend para que
     * haga broadcast por WebSocket a todos los navegadores conectados.
     *
     * @param dto el estado actualizado del pedido
     */
    public void notificarActualizacion(OrderStatusDTO dto) {
        try {
            String url = backendUrl + "/internal/update";
            // Usar HttpEntity con Content-Type explícito para evitar problemas de serialización
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<OrderStatusDTO> entity =
                    new org.springframework.http.HttpEntity<>(dto, headers);
            ResponseEntity<Void> response = restTemplate.postForEntity(url, entity, Void.class);
            log.debug("[dashboard_aggregator] Notificación enviada al backend — orderId={} status={}",
                    dto.orderId(), response.getStatusCode());
        } catch (RestClientException e) {
            log.warn("[dashboard_aggregator] No se pudo notificar al dashboard_backend — orderId={}: {}",
                    dto.orderId(), e.getMessage());
        }
    }
}
