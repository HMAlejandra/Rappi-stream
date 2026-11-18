package com.rappistream.orderprocessor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappistream.common.enums.OrderEventType;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.common.events.payloads.DriverAssignedPayload;
import com.rappistream.orderprocessor.domain.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Implementa OrderEventPublisherPort usando Kafka.
 * Publica en order_events con orderId como clave de partición.
 */
@Component
public class KafkaOrderEventPublisher implements OrderEventPublisherPort {

    private static final Logger log   = LoggerFactory.getLogger(KafkaOrderEventPublisher.class);
    private static final String TOPIC = "order_events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaOrderEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                     ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper  = objectMapper;
    }

    @Override
    public void publicarOrderCreated(Pedido pedido) {
        String product      = escape(pedido.getProduct());
        String customerName = escape(pedido.getCustomerName());
        String address      = pedido.getAddress() != null ? escape(pedido.getAddress()) : "";
        String payload = String.format(
                "{\"orderId\":\"%s\",\"customerName\":\"%s\",\"product\":\"%s\",\"quantity\":%d,\"address\":\"%s\"}",
                pedido.getId(), customerName, product, pedido.getQuantity(), address);
        publicar(pedido.getId(), OrderEventType.ORDER_CREATED, payload);
        log.info("[order_processor] ORDER_CREATED publicado — orderId={}", pedido.getId());
    }

    @Override
    public void publicarDriverAssigned(Pedido pedido) {
        try {
            String payload = objectMapper.writeValueAsString(
                    new DriverAssignedPayload(pedido.getDriverId(), pedido.getDriverName()));
            publicar(pedido.getId(), OrderEventType.DRIVER_ASSIGNED, payload);
            log.info("[order_processor] DRIVER_ASSIGNED publicado — orderId={} driverId={}",
                    pedido.getId(), pedido.getDriverId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error publicando DRIVER_ASSIGNED", e);
        }
    }

    private void publicar(String orderId, OrderEventType eventType, String payloadJson) {
        try {
            String envelopeJson = objectMapper.writeValueAsString(new OrderEventEnvelope(
                    UUID.randomUUID().toString(), orderId, eventType, Instant.now(), payloadJson));
            kafkaTemplate.send(TOPIC, orderId, envelopeJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error publicando evento " + eventType, e);
        }
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
