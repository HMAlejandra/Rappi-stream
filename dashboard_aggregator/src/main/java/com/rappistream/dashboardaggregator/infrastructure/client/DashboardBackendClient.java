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
            @Value("${dashboard.backend.url:http://dashboard_backend:8087}") String backendUrl) {
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
            ResponseEntity<Void> response = restTemplate.postForEntity(url, dto, Void.class);
            log.debug("[dashboard_aggregator] Notificación enviada al backend — orderId={} status={}",
                    dto.orderId(), response.getStatusCode());
        } catch (RestClientException e) {
            // Dashboard backend no disponible — no es crítico para el flujo de eventos
            log.warn("[dashboard_aggregator] No se pudo notificar al dashboard_backend — orderId={}: {}",
                    dto.orderId(), e.getMessage());
        }
    }
}
