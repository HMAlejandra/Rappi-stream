package com.rappistream.notificationrouter.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.notificationrouter.service.NotificationRouterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listener de Kafka para el topic order_events.
 *
 * SRP: deserializa el mensaje y delega al servicio.
 * No contiene lógica de routing ni de notificación.
 *
 * Consume todos los eventos — el servicio decide cuáles
 * requieren notificación a través del Patrón Strategy.
 */
@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final ObjectMapper objectMapper;
    private final NotificationRouterService routerService;

    public OrderEventsListener(ObjectMapper objectMapper,
                                NotificationRouterService routerService) {
        this.objectMapper  = objectMapper;
        this.routerService = routerService;
    }

    @KafkaListener(
            topics = "order_events",
            groupId = "notification-router-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderEvent(String mensajeJson) {
        try {
            OrderEventEnvelope envelope =
                    objectMapper.readValue(mensajeJson, OrderEventEnvelope.class);

            log.debug("[notification_router] Evento recibido: {} — orderId={}",
                    envelope.eventType(), envelope.orderId());

            routerService.procesarEvento(envelope);

        } catch (Exception e) {
            log.error("[notification_router] Error procesando evento: {}", mensajeJson, e);
            // No relanzar: el consumidor continúa con el siguiente mensaje
        }
    }
}
