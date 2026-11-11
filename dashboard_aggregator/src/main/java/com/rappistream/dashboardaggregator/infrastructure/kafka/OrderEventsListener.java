package com.rappistream.dashboardaggregator.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappistream.common.dto.OrderStatusDTO;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.dashboardaggregator.application.OrderAggregatorService;
import com.rappistream.dashboardaggregator.infrastructure.client.DashboardBackendClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listener Kafka del dashboard_aggregator.
 *
 * Consume TODOS los eventos del topic order_events desde el principio.
 * groupId único: "dashboard-aggregator-group"
 * auto-offset-reset: earliest → reprocesa histórico al arrancar
 *
 * SRP: solo recibe, deserializa y delega.
 * No contiene lógica de estado ni de notificación.
 *
 * Flujo:
 * Kafka → deserializa envelope → procesarEvento() → notificar backend
 */
@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final ObjectMapper             objectMapper;
    private final OrderAggregatorService   aggregatorService;
    private final DashboardBackendClient   backendClient;

    public OrderEventsListener(ObjectMapper objectMapper,
                                OrderAggregatorService aggregatorService,
                                DashboardBackendClient backendClient) {
        this.objectMapper      = objectMapper;
        this.aggregatorService = aggregatorService;
        this.backendClient     = backendClient;
    }

    @KafkaListener(
            topics          = "order_events",
            groupId         = "dashboard-aggregator-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderEvent(String mensajeJson) {
        try {
            OrderEventEnvelope envelope =
                    objectMapper.readValue(mensajeJson, OrderEventEnvelope.class);

            log.debug("[dashboard_aggregator] Evento recibido — tipo={} orderId={}",
                    envelope.eventType(), envelope.orderId());

            OrderStatusDTO dto = aggregatorService.procesarEvento(envelope);

            // Notificar al dashboard_backend solo si el evento cambió el estado
            if (dto != null) {
                backendClient.notificarActualizacion(dto);
            }

        } catch (Exception e) {
            log.error("[dashboard_aggregator] Error procesando mensaje Kafka: {}", mensajeJson, e);
            // No relanzar — el consumidor continúa con el siguiente mensaje
        }
    }
}
