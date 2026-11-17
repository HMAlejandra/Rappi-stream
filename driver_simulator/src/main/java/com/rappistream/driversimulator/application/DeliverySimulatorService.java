package com.rappistream.driversimulator.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappistream.common.enums.OrderEventType;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.common.events.payloads.DeliveryStartedPayload;
import com.rappistream.common.events.payloads.DriverArrivingPayload;
import com.rappistream.common.events.payloads.DriverAssignedPayload;
import com.rappistream.common.events.payloads.OrderDeliveredPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Servicio de aplicación — simula el ciclo de entrega de forma asíncrona (@Async).
 * Publica DELIVERY_STARTED → DRIVER_ARRIVING → ORDER_DELIVERED en Kafka,
 * usando orderId como clave de partición para garantizar orden.
 */
@Service
public class DeliverySimulatorService {

    private static final Logger log   = LoggerFactory.getLogger(DeliverySimulatorService.class);
    private static final String TOPIC = "order_events";

    @Value("${simulator.delay.delivery-started-ms:5000}") private long deliveryStartedDelayMs;
    @Value("${simulator.delay.driver-arriving-ms:8000}")  private long driverArrivingDelayMs;
    @Value("${simulator.delay.order-delivered-ms:5000}")  private long orderDeliveredDelayMs;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public DeliverySimulatorService(KafkaTemplate<String, String> kafkaTemplate,
                                     ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper  = objectMapper;
    }

    @Async
    public void simularCicloEntrega(OrderEventEnvelope envelope, DriverAssignedPayload payload) {
        String orderId    = envelope.orderId();
        String driverId   = payload.driverId();
        String driverName = payload.driverName();

        log.info("[driver_simulator] Iniciando ciclo de entrega — orderId={} driverId={} driverName={}",
                orderId, driverId, driverName);

        try {
            sleep(deliveryStartedDelayMs);
            publicarEvento(orderId, OrderEventType.DELIVERY_STARTED,
                    new DeliveryStartedPayload(driverId, 18));
            log.info("[driver_simulator] DELIVERY_STARTED publicado — orderId={}", orderId);

            sleep(driverArrivingDelayMs);
            publicarEvento(orderId, OrderEventType.DRIVER_ARRIVING,
                    new DriverArrivingPayload(driverId, driverName, "2 minutos"));
            log.info("[driver_simulator] DRIVER_ARRIVING publicado — orderId={}", orderId);

            sleep(orderDeliveredDelayMs);
            publicarEvento(orderId, OrderEventType.ORDER_DELIVERED,
                    new OrderDeliveredPayload(driverId, driverName, Instant.now()));
            log.info("[driver_simulator] ORDER_DELIVERED publicado — orderId={} ✅", orderId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[driver_simulator] Ciclo interrumpido — orderId={}", orderId, e);
        } catch (JsonProcessingException e) {
            log.error("[driver_simulator] Error serializando payload — orderId={}", orderId, e);
        }
    }

    private void publicarEvento(String orderId, OrderEventType eventType, Object payloadObj)
            throws JsonProcessingException {
        String envelopeJson = objectMapper.writeValueAsString(new OrderEventEnvelope(
                UUID.randomUUID().toString(), orderId, eventType, Instant.now(),
                objectMapper.writeValueAsString(payloadObj)));
        kafkaTemplate.send(TOPIC, orderId, envelopeJson);
    }

    private void sleep(long ms) throws InterruptedException {
        log.debug("[driver_simulator] Esperando {}ms...", ms);
        Thread.sleep(ms);
    }
}
