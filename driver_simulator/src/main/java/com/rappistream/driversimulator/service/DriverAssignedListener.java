package com.rappistream.driversimulator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappistream.common.enums.OrderEventType;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.common.events.payloads.DriverAssignedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listener de Kafka — consume el topic order_events.
 *
 * SRP: recibe el mensaje, filtra DRIVER_ASSIGNED y delega al servicio.
 * No contiene lógica de simulación.
 */
@Component
public class DriverAssignedListener {

    private static final Logger log = LoggerFactory.getLogger(DriverAssignedListener.class);

    private final ObjectMapper objectMapper;
    private final DeliverySimulatorService deliverySimulatorService;

    public DriverAssignedListener(ObjectMapper objectMapper,
                                   DeliverySimulatorService deliverySimulatorService) {
        this.objectMapper             = objectMapper;
        this.deliverySimulatorService = deliverySimulatorService;
    }

    @KafkaListener(
            topics = "order_events",
            groupId = "driver-simulator-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderEvent(String mensajeJson) {
        try {
            OrderEventEnvelope envelope =
                    objectMapper.readValue(mensajeJson, OrderEventEnvelope.class);

            if (envelope.eventType() != OrderEventType.DRIVER_ASSIGNED) {
                log.debug("[driver_simulator] Evento ignorado: {} — orderId={}",
                        envelope.eventType(), envelope.orderId());
                return;
            }

            log.info("[driver_simulator] DRIVER_ASSIGNED recibido — orderId={}",
                    envelope.orderId());

            DriverAssignedPayload payload =
                    objectMapper.readValue(envelope.payload(), DriverAssignedPayload.class);

            deliverySimulatorService.simularCicloEntrega(envelope, payload);

        } catch (Exception e) {
            log.error("[driver_simulator] Error procesando mensaje: {}", mensajeJson, e);
        }
    }
}
