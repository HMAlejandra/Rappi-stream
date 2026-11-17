package com.rappistream.driversimulator.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rappistream.common.enums.OrderEventType;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.common.events.payloads.DriverAssignedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliverySimulatorServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Captor private ArgumentCaptor<String> topicCaptor;
    @Captor private ArgumentCaptor<String> keyCaptor;
    @Captor private ArgumentCaptor<String> valueCaptor;

    private DeliverySimulatorService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        service = new DeliverySimulatorService(kafkaTemplate, objectMapper);

        ReflectionTestUtils.setField(service, "deliveryStartedDelayMs", 0L);
        ReflectionTestUtils.setField(service, "driverArrivingDelayMs",  0L);
        ReflectionTestUtils.setField(service, "orderDeliveredDelayMs",  0L);
    }

    private OrderEventEnvelope envelopeDriverAssigned(String orderId) throws Exception {
        DriverAssignedPayload payload = new DriverAssignedPayload("D-001", "Carlos Mendoza");
        return new OrderEventEnvelope("evt-001", orderId, OrderEventType.DRIVER_ASSIGNED,
                Instant.now(), objectMapper.writeValueAsString(payload));
    }

    private DriverAssignedPayload payloadDriverAssigned() {
        return new DriverAssignedPayload("D-001", "Carlos Mendoza");
    }

    @Test
    @DisplayName("Debe publicar exactamente 3 eventos en Kafka")
    void debePublicarTresEventos() throws Exception {
        service.simularCicloEntrega(envelopeDriverAssigned("order-123"), payloadDriverAssigned());
        verify(kafkaTemplate, times(3)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Los 3 eventos deben publicarse en el topic order_events")
    void debePublicarEnTopicCorrecto() throws Exception {
        service.simularCicloEntrega(envelopeDriverAssigned("order-123"), payloadDriverAssigned());
        verify(kafkaTemplate, times(3)).send(eq("order_events"), anyString(), anyString());
    }

    @Test
    @DisplayName("Todos los eventos deben usar orderId como clave de partición")
    void debeUsarOrderIdComoClaveDeParticion() throws Exception {
        String orderId = "order-456";
        service.simularCicloEntrega(envelopeDriverAssigned(orderId), payloadDriverAssigned());
        verify(kafkaTemplate, times(3)).send(anyString(), eq(orderId), anyString());
    }

    @Test
    @DisplayName("Los eventos deben publicarse en el orden correcto: STARTED → ARRIVING → DELIVERED")
    void debePublicarEnOrdenCorrecto() throws Exception {
        service.simularCicloEntrega(envelopeDriverAssigned("order-789"), payloadDriverAssigned());
        verify(kafkaTemplate, times(3)).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());
        List<String> mensajes = valueCaptor.getAllValues();
        assertThat(mensajes.get(0)).contains("DELIVERY_STARTED");
        assertThat(mensajes.get(1)).contains("DRIVER_ARRIVING");
        assertThat(mensajes.get(2)).contains("ORDER_DELIVERED");
    }

    @Test
    @DisplayName("Cada evento debe contener el orderId en el envelope")
    void cadaEventoDebeContenerElOrderId() throws Exception {
        String orderId = "order-999";
        service.simularCicloEntrega(envelopeDriverAssigned(orderId), payloadDriverAssigned());
        verify(kafkaTemplate, times(3)).send(anyString(), anyString(), valueCaptor.capture());
        for (String mensajeJson : valueCaptor.getAllValues()) {
            assertThat(mensajeJson).contains(orderId);
        }
    }

    @Test
    @DisplayName("El evento ORDER_DELIVERED debe contener el driverId del payload recibido")
    void orderDeliveredDebeContenerDriverId() throws Exception {
        String orderId  = "order-111";
        String driverId = "D-003";
        DriverAssignedPayload payload = new DriverAssignedPayload(driverId, "Andrés Torres");
        OrderEventEnvelope envelope = new OrderEventEnvelope("evt-002", orderId,
                OrderEventType.DRIVER_ASSIGNED, Instant.now(), objectMapper.writeValueAsString(payload));

        service.simularCicloEntrega(envelope, payload);

        verify(kafkaTemplate, times(3)).send(anyString(), anyString(), valueCaptor.capture());
        String orderDeliveredJson = valueCaptor.getAllValues().get(2);
        assertThat(orderDeliveredJson).contains("ORDER_DELIVERED");
        assertThat(orderDeliveredJson).contains(driverId);
    }
}
