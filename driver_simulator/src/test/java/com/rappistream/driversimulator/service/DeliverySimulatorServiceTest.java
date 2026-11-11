package com.rappistream.driversimulator.service;

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

/**
 * Tests unitarios para DeliverySimulatorService.
 *
 * Estrategia:
 * - Se mockea KafkaTemplate para verificar qué eventos se publican
 *   sin necesitar un Kafka real (pruebas rápidas, sin infra).
 * - Se usan delays de 0ms (inyectados vía ReflectionTestUtils)
 *   para que los tests corran en milisegundos.
 * - Se llama al método directamente (sin @Async) para mantener
 *   la ejecución en el hilo del test y poder hacer asserts.
 *
 * Patrón AAA: Arrange / Act / Assert en cada test.
 */
@ExtendWith(MockitoExtension.class)
class DeliverySimulatorServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Captor
    private ArgumentCaptor<String> topicCaptor;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    @Captor
    private ArgumentCaptor<String> valueCaptor;

    private DeliverySimulatorService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        service = new DeliverySimulatorService(kafkaTemplate, objectMapper);

        // Delays a 0ms para que los tests no tarden
        ReflectionTestUtils.setField(service, "deliveryStartedDelayMs", 0L);
        ReflectionTestUtils.setField(service, "driverArrivingDelayMs",  0L);
        ReflectionTestUtils.setField(service, "orderDeliveredDelayMs",  0L);
    }

    // --- helpers ---

    private OrderEventEnvelope envelopeDriverAssigned(String orderId) throws Exception {
        DriverAssignedPayload payload = new DriverAssignedPayload("D-001", "Carlos Mendoza");
        return new OrderEventEnvelope(
                "evt-001",
                orderId,
                OrderEventType.DRIVER_ASSIGNED,
                Instant.now(),
                objectMapper.writeValueAsString(payload)
        );
    }

    private DriverAssignedPayload payloadDriverAssigned() {
        return new DriverAssignedPayload("D-001", "Carlos Mendoza");
    }

    // ------------------------------------------------
    // Tests
    // ------------------------------------------------

    @Test
    @DisplayName("Debe publicar exactamente 3 eventos en Kafka")
    void debePublicarTresEventos() throws Exception {
        // Arrange
        String orderId = "order-123";
        OrderEventEnvelope envelope = envelopeDriverAssigned(orderId);
        DriverAssignedPayload payload = payloadDriverAssigned();

        // Act
        service.simularCicloEntrega(envelope, payload);

        // Assert
        verify(kafkaTemplate, times(3)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Los 3 eventos deben publicarse en el topic order_events")
    void debePublicarEnTopicCorrecto() throws Exception {
        // Arrange
        String orderId = "order-123";
        OrderEventEnvelope envelope = envelopeDriverAssigned(orderId);
        DriverAssignedPayload payload = payloadDriverAssigned();

        // Act
        service.simularCicloEntrega(envelope, payload);

        // Assert
        verify(kafkaTemplate, times(3)).send(eq("order_events"), anyString(), anyString());
    }

    @Test
    @DisplayName("Todos los eventos deben usar orderId como clave de partición")
    void debeUsarOrderIdComoClaveDeParticion() throws Exception {
        // Arrange
        String orderId = "order-456";
        OrderEventEnvelope envelope = envelopeDriverAssigned(orderId);
        DriverAssignedPayload payload = payloadDriverAssigned();

        // Act
        service.simularCicloEntrega(envelope, payload);

        // Assert
        verify(kafkaTemplate, times(3)).send(anyString(), eq(orderId), anyString());
    }

    @Test
    @DisplayName("Los eventos deben publicarse en el orden correcto: STARTED → ARRIVING → DELIVERED")
    void debePublicarEnOrdenCorrecto() throws Exception {
        // Arrange
        String orderId = "order-789";
        OrderEventEnvelope envelope = envelopeDriverAssigned(orderId);
        DriverAssignedPayload payload = payloadDriverAssigned();

        // Act
        service.simularCicloEntrega(envelope, payload);

        // Assert — capturamos los 3 mensajes JSON en orden
        verify(kafkaTemplate, times(3)).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());
        List<String> mensajes = valueCaptor.getAllValues();

        assertThat(mensajes.get(0)).contains("DELIVERY_STARTED");
        assertThat(mensajes.get(1)).contains("DRIVER_ARRIVING");
        assertThat(mensajes.get(2)).contains("ORDER_DELIVERED");
    }

    @Test
    @DisplayName("Cada evento debe contener el orderId en el envelope")
    void cadaEventoDebeContenerElOrderId() throws Exception {
        // Arrange
        String orderId = "order-999";
        OrderEventEnvelope envelope = envelopeDriverAssigned(orderId);
        DriverAssignedPayload payload = payloadDriverAssigned();

        // Act
        service.simularCicloEntrega(envelope, payload);

        // Assert
        verify(kafkaTemplate, times(3)).send(anyString(), anyString(), valueCaptor.capture());
        List<String> mensajes = valueCaptor.getAllValues();

        for (String mensajeJson : mensajes) {
            assertThat(mensajeJson).contains(orderId);
        }
    }

    @Test
    @DisplayName("El evento ORDER_DELIVERED debe contener el driverId del payload recibido")
    void orderDeliveredDebeContenerDriverId() throws Exception {
        // Arrange
        String orderId = "order-111";
        String driverId = "D-003";
        DriverAssignedPayload payload = new DriverAssignedPayload(driverId, "Andrés Torres");
        OrderEventEnvelope envelope = new OrderEventEnvelope(
                "evt-002", orderId, OrderEventType.DRIVER_ASSIGNED,
                Instant.now(), objectMapper.writeValueAsString(payload)
        );

        // Act
        service.simularCicloEntrega(envelope, payload);

        // Assert — el tercer mensaje es ORDER_DELIVERED
        verify(kafkaTemplate, times(3)).send(anyString(), anyString(), valueCaptor.capture());
        String orderDeliveredJson = valueCaptor.getAllValues().get(2);

        assertThat(orderDeliveredJson).contains("ORDER_DELIVERED");
        assertThat(orderDeliveredJson).contains(driverId);
    }
}
