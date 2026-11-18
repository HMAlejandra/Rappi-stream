package com.rappistream.dashboardaggregator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappistream.common.dto.OrderStatusDTO;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.dashboardaggregator.repository.DashboardBackendClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listener Kafka del dashboard_aggregator.
 *
 * SRP: recibe, deserializa y delega.
 * No contiene lógica de estado ni de notificación.
 */
@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final ObjectMapper           objectMapper;
    private final OrderAggregatorService aggregatorService;
    private final DashboardBackendClient backendClient;

    public OrderEventsListener(ObjectMapper objectMapper,
                                OrderAggregatorService aggregatorService,
                                DashboardBackendClient backendClient) {
        this.objectMapper      = objectMapper;
        this.aggregatorService = aggregatorService;
        this.backendClient     = backendClient;
    }

    @KafkaListener(
            topics           = "order_events",
            groupId          = "dashboard-aggregator-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderEvent(String mensajeJson) {
        try {
            OrderEventEnvelope envelope =
                    objectMapper.readValue(mensajeJson, OrderEventEnvelope.class);
            log.debug("[dashboard_aggregator] Evento recibido — tipo={} orderId={}",
                    envelope.eventType(), envelope.orderId());

            OrderStatusDTO dto = aggregatorService.procesarEvento(envelope);
            if (dto != null) {
                backendClient.notificarActualizacion(dto);
            }
        } catch (Exception e) {
            log.error("[dashboard_aggregator] Error procesando mensaje Kafka: {}", mensajeJson, e);
        }
    }
}
