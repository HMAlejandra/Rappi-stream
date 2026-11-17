package com.rappistream.notificationrouter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rappistream.common.enums.OrderEventType;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.common.events.payloads.DriverArrivingPayload;
import com.rappistream.common.events.payloads.OrderDeliveredPayload;
import com.rappistream.notificationrouter.config.RabbitMqConfig;
import com.rappistream.notificationrouter.service.strategy.DriverArrivingSmsStrategy;
import com.rappistream.notificationrouter.service.NotificationStrategy;
import com.rappistream.notificationrouter.service.strategy.OrderDeliveredPushStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para NotificationRouterService.
 * Verifica el PatrÃ³n Strategy: cada evento dispara la notificaciÃ³n correcta.
 */
@ExtendWith(MockitoExtension.class)
class NotificationRouterServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private NotificationRouterService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        List<NotificationStrategy> estrategias = List.of(
                new DriverArrivingSmsStrategy(objectMapper),
                new OrderDeliveredPushStrategy(objectMapper)
        );

        service = new NotificationRouterService(estrategias, rabbitTemplate);
    }

    @Test
    @DisplayName("DRIVER_ARRIVING â†’ publica en exchange con routing key SMS")
    void driverArriving_publicaEnSmsQueue() throws Exception {
        // Arrange
        DriverArrivingPayload payload = new DriverArrivingPayload("D-001", "Carlos", "2 minutos");
        OrderEventEnvelope envelope = new OrderEventEnvelope(
                "evt-1", "order-1", OrderEventType.DRIVER_ARRIVING,
                Instant.now(), objectMapper.writeValueAsString(payload)
        );

        // Act
        service.procesarEvento(envelope);

        // Assert
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMqConfig.NOTIFICATIONS_EXCHANGE),
                eq(RabbitMqConfig.ROUTING_KEY_SMS),
                any(Object.class)
        );
    }

    @Test
    @DisplayName("ORDER_DELIVERED â†’ publica en exchange con routing key PUSH")
    void orderDelivered_publicaEnPushQueue() throws Exception {
        // Arrange
        OrderDeliveredPayload payload = new OrderDeliveredPayload("D-002", "Laura", Instant.now());
        OrderEventEnvelope envelope = new OrderEventEnvelope(
                "evt-2", "order-2", OrderEventType.ORDER_DELIVERED,
                Instant.now(), objectMapper.writeValueAsString(payload)
        );

        // Act
        service.procesarEvento(envelope);

        // Assert
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMqConfig.NOTIFICATIONS_EXCHANGE),
                eq(RabbitMqConfig.ROUTING_KEY_PUSH),
                any(Object.class)
        );
    }

    @Test
    @DisplayName("DRIVER_ASSIGNED â†’ no publica ninguna notificaciÃ³n")
    void driverAssigned_noPublicaNada() throws Exception {
        // Arrange
        OrderEventEnvelope envelope = new OrderEventEnvelope(
                "evt-3", "order-3", OrderEventType.DRIVER_ASSIGNED,
                Instant.now(), "{\"driverId\":\"D-001\",\"driverName\":\"Carlos\"}"
        );

        // Act
        service.procesarEvento(envelope);

        // Assert
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("DELIVERY_STARTED â†’ no publica ninguna notificaciÃ³n")
    void deliveryStarted_noPublicaNada() throws Exception {
        // Arrange
        OrderEventEnvelope envelope = new OrderEventEnvelope(
                "evt-4", "order-4", OrderEventType.DELIVERY_STARTED,
                Instant.now(), "{\"driverId\":\"D-001\",\"estimatedArrivalSeconds\":18}"
        );

        // Act
        service.procesarEvento(envelope);

        // Assert
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("DRIVER_ARRIVING â†’ el NotificationCommand contiene el orderId correcto")
    void driverArriving_comandoContieneOrderId() throws Exception {
        // Arrange
        String orderId = "order-999";
        DriverArrivingPayload payload = new DriverArrivingPayload("D-003", "AndrÃ©s", "llegando ahora");
        OrderEventEnvelope envelope = new OrderEventEnvelope(
                "evt-5", orderId, OrderEventType.DRIVER_ARRIVING,
                Instant.now(), objectMapper.writeValueAsString(payload)
        );

        ArgumentCaptor<Object> commandCaptor = ArgumentCaptor.forClass(Object.class);

        // Act
        service.procesarEvento(envelope);

        // Assert
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), commandCaptor.capture());
        String commandJson = objectMapper.writeValueAsString(commandCaptor.getValue());
        assertThat(commandJson).contains(orderId);
    }
}

