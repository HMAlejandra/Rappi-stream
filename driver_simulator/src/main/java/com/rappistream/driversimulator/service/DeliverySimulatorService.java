package com.rappistream.driversimulator.service;

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
 * Servicio que simula el ciclo de vida de una entrega.
 *
 * Flujo (todos los eventos van al topic order_events con orderId como clave):
 *
 *   DRIVER_ASSIGNED recibido
 *        │
 *        ▼
 *   [delay: deliveryStartedDelayMs]
 *        │
 *        ▼
 *   publica DELIVERY_STARTED
 *        │
 *        ▼
 *   [delay: driverArrivingDelayMs]
 *        │
 *        ▼
 *   publica DRIVER_ARRIVING
 *        │
 *        ▼
 *   [delay: orderDeliveredDelayMs]
 *        │
 *        ▼
 *   publica ORDER_DELIVERED
 *
 * POO — Separación de responsabilidades:
 * El listener (@KafkaListener) solo parsea y delega.
 * Este servicio contiene toda la lógica de simulación.
 *
 * @Async: el ciclo de entrega corre en un hilo separado para no
 * bloquear el hilo de Kafka (que necesita seguir procesando mensajes).
 */
@Service
public class DeliverySimulatorService {

    private static final Logger log = LoggerFactory.getLogger(DeliverySimulatorService.class);

    private static final String TOPIC = "order_events";

    // Tiempos de simulación configurables desde application.properties
    @Value("${simulator.delay.delivery-started-ms:5000}")
    private long deliveryStartedDelayMs;

    @Value("${simulator.delay.driver-arriving-ms:8000}")
    private long driverArrivingDelayMs;

    @Value("${simulator.delay.order-delivered-ms:5000}")
    private long orderDeliveredDelayMs;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public DeliverySimulatorService(KafkaTemplate<String, String> kafkaTemplate,
                                     ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Simula el ciclo completo de entrega de forma asíncrona.
     *
     * @param envelope el evento DRIVER_ASSIGNED recibido de Kafka
     * @param payload  el payload ya deserializado del envelope
     */
    @Async
    public void simularCicloEntrega(OrderEventEnvelope envelope, DriverAssignedPayload payload) {
        String orderId = envelope.orderId();
        String driverId = payload.driverId();
        String driverName = payload.driverName();

        log.info("[driver_simulator] Iniciando ciclo de entrega — orderId={} driverId={} driverName={}",
                orderId, driverId, driverName);

        try {
            // 1. DELIVERY_STARTED — el repartidor inició el recorrido
            sleep(deliveryStartedDelayMs);
            publicarEvento(orderId, OrderEventType.DELIVERY_STARTED,
                    new DeliveryStartedPayload(driverId, 18)); // 18 seg ETA simulado
            log.info("[driver_simulator] DELIVERY_STARTED publicado — orderId={}", orderId);

            // 2. DRIVER_ARRIVING — el repartidor está llegando
            sleep(driverArrivingDelayMs);
            publicarEvento(orderId, OrderEventType.DRIVER_ARRIVING,
                    new DriverArrivingPayload(driverId, driverName, "2 minutos"));
            log.info("[driver_simulator] DRIVER_ARRIVING publicado — orderId={}", orderId);

            // 3. ORDER_DELIVERED — entrega completada (evento terminal)
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

    /**
     * Construye y publica un OrderEventEnvelope en Kafka.
     * Usa orderId como clave de partición para garantizar orden.
     */
    private void publicarEvento(String orderId, OrderEventType eventType, Object payloadObj)
            throws JsonProcessingException {

        String payloadJson = objectMapper.writeValueAsString(payloadObj);

        OrderEventEnvelope evento = new OrderEventEnvelope(
                UUID.randomUUID().toString(), // eventId único
                orderId,
                eventType,
                Instant.now(),
                payloadJson
        );

        String envelopeJson = objectMapper.writeValueAsString(evento);

        // orderId como clave → misma partición → orden garantizado por Kafka
        kafkaTemplate.send(TOPIC, orderId, envelopeJson);
    }

    private void sleep(long ms) throws InterruptedException {
        log.debug("[driver_simulator] Esperando {}ms...", ms);
        Thread.sleep(ms);
    }
}
