package com.rappistream.driversimulator.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappistream.common.enums.OrderEventType;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.common.events.payloads.DriverAssignedPayload;
import com.rappistream.driversimulator.service.DeliverySimulatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listener de Kafka para el topic order_events.
 *
 * Responsabilidad única (SRP):
 * Solo se encarga de recibir el mensaje, deserializarlo y delegar
 * al servicio. No contiene lógica de negocio.
 *
 * Filtro:
 * Solo procesa mensajes con eventType == DRIVER_ASSIGNED.
 * El resto (ORDER_CREATED, DELIVERY_STARTED, etc.) se ignoran
 * con un log de debug para no generar ruido.
 *
 * Manejo de errores:
 * Si la deserialización falla, se loguea el error y se descarta
 * el mensaje (no se relanza la excepción) para evitar que el
 * consumidor se trabe en un mensaje malformado.
 */
@Component
public class DriverAssignedListener {

    private static final Logger log = LoggerFactory.getLogger(DriverAssignedListener.class);

    private final ObjectMapper objectMapper;
    private final DeliverySimulatorService deliverySimulatorService;

    public DriverAssignedListener(ObjectMapper objectMapper,
                                   DeliverySimulatorService deliverySimulatorService) {
        this.objectMapper = objectMapper;
        this.deliverySimulatorService = deliverySimulatorService;
    }

    @KafkaListener(
            topics = "order_events",
            groupId = "driver-simulator-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderEvent(String mensajeJson) {
        try {
            OrderEventEnvelope envelope = objectMapper.readValue(mensajeJson, OrderEventEnvelope.class);

            // Solo reaccionamos a DRIVER_ASSIGNED
            if (envelope.eventType() != OrderEventType.DRIVER_ASSIGNED) {
                log.debug("[driver_simulator] Evento ignorado: {} — orderId={}",
                        envelope.eventType(), envelope.orderId());
                return;
            }

            log.info("[driver_simulator] DRIVER_ASSIGNED recibido — orderId={} eventId={}",
                    envelope.orderId(), envelope.eventId());

            DriverAssignedPayload payload =
                    objectMapper.readValue(envelope.payload(), DriverAssignedPayload.class);

            // Delega al servicio en hilo asíncrono
            deliverySimulatorService.simularCicloEntrega(envelope, payload);

        } catch (Exception e) {
            log.error("[driver_simulator] Error procesando mensaje de Kafka: {}", mensajeJson, e);
            // No se relanza: el consumidor continúa con el siguiente mensaje
        }
    }
}
