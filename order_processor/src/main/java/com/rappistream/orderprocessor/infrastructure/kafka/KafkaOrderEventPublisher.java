package com.rappistream.orderprocessor.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappistream.common.enums.OrderEventType;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.common.events.payloads.DriverAssignedPayload;
import com.rappistream.orderprocessor.domain.model.Pedido;
import com.rappistream.orderprocessor.domain.port.OrderEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Adaptador Kafka que implementa OrderEventPublisherPort.
 *
 * DIP en acción:
 * El dominio define OrderEventPublisherPort.
 * Esta clase (infraestructura) la implementa con KafkaTemplate.
 *
 * Publica en el topic order_events usando orderId como clave
 * de partición para garantizar orden por pedido.
 *
 * El payload se serializa como JSON String dentro del envelope,
 * igual que en driver_simulator (mismo contrato OrderEventEnvelope).
 */
@Component
public class KafkaOrderEventPublisher implements OrderEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderEventPublisher.class);
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
        // ORDER_CREATED incluye todos los datos del pedido para que el aggregator
        // pueda construir el snapshot completo sin esperar DRIVER_ASSIGNED
        String product      = pedido.getProduct().replace("\\", "\\\\").replace("\"", "\\\"");
        String customerName = pedido.getCustomerName().replace("\\", "\\\\").replace("\"", "\\\"");
        String address      = pedido.getAddress() != null
                ? pedido.getAddress().replace("\\", "\\\\").replace("\"", "\\\"")
                : "";
        String payloadJson = String.format(
                "{\"orderId\":\"%s\",\"customerName\":\"%s\",\"product\":\"%s\",\"quantity\":%d,\"address\":\"%s\"}",
                pedido.getId(), customerName, product, pedido.getQuantity(), address
        );
        publicar(pedido.getId(), OrderEventType.ORDER_CREATED, payloadJson);
        log.info("[order_processor] ORDER_CREATED publicado — orderId={}", pedido.getId());
    }

    @Override
    public void publicarDriverAssigned(Pedido pedido) {
        try {
            DriverAssignedPayload payload = new DriverAssignedPayload(
                    pedido.getDriverId(),
                    pedido.getDriverName()
            );
            String payloadJson = objectMapper.writeValueAsString(payload);
            publicar(pedido.getId(), OrderEventType.DRIVER_ASSIGNED, payloadJson);
            log.info("[order_processor] DRIVER_ASSIGNED publicado — orderId={} driverId={}",
                    pedido.getId(), pedido.getDriverId());
        } catch (JsonProcessingException e) {
            log.error("[order_processor] Error serializando DriverAssignedPayload — orderId={}",
                    pedido.getId(), e);
            throw new RuntimeException("Error publicando DRIVER_ASSIGNED", e);
        }
    }

    private void publicar(String orderId, OrderEventType eventType, String payloadJson) {
        try {
            OrderEventEnvelope envelope = new OrderEventEnvelope(
                    UUID.randomUUID().toString(),
                    orderId,
                    eventType,
                    Instant.now(),
                    payloadJson
            );
            String envelopeJson = objectMapper.writeValueAsString(envelope);
            // orderId como clave → misma partición → orden garantizado
            kafkaTemplate.send(TOPIC, orderId, envelopeJson);
        } catch (JsonProcessingException e) {
            log.error("[order_processor] Error serializando envelope — orderId={}", orderId, e);
            throw new RuntimeException("Error publicando evento " + eventType, e);
        }
    }
}
