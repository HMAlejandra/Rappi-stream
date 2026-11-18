package com.rappistream.dashboardaggregator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rappistream.common.dto.OrderStatusDTO;
import com.rappistream.common.enums.OrderEventType;
import com.rappistream.common.enums.OrderStatus;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.common.events.payloads.DriverAssignedPayload;
import com.rappistream.dashboardaggregator.domain.OrderEventLogPort;
import com.rappistream.dashboardaggregator.domain.OrderPersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderAggregatorService — Consumidor Stateful")
class OrderAggregatorServiceTest {

    @Mock private OrderPersistencePort persistencePort;
    @Mock private OrderEventLogPort    eventLogPort;

    private ObjectMapper objectMapper;
    private OrderAggregatorService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        service = new OrderAggregatorService(persistencePort, eventLogPort, objectMapper);
        when(eventLogPort.existeEvento(anyString())).thenReturn(false);
        lenient().when(persistencePort.buscarPorId(anyString())).thenReturn(Optional.empty());
    }

    private OrderEventEnvelope envelopeOrderCreated(String orderId) throws Exception {
        String payload = String.format(
                "{\"orderId\":\"%s\",\"customerName\":\"María García\",\"product\":\"Hamburguesa\",\"quantity\":2,\"address\":\"Calle 100\"}",
                orderId);
        return new OrderEventEnvelope(UUID.randomUUID().toString(), orderId,
                OrderEventType.ORDER_CREATED, Instant.now(), payload);
    }

    private OrderEventEnvelope envelopeDriverAssigned(String orderId) throws Exception {
        return new OrderEventEnvelope(UUID.randomUUID().toString(), orderId,
                OrderEventType.DRIVER_ASSIGNED, Instant.now(),
                objectMapper.writeValueAsString(new DriverAssignedPayload("D-001", "Carlos")));
    }

    private OrderEventEnvelope envelopeSimple(String orderId, OrderEventType tipo) {
        return new OrderEventEnvelope(UUID.randomUUID().toString(), orderId,
                tipo, Instant.now(), "{\"driverId\":\"D-001\"}");
    }

    private void resetMocks() {
        reset(eventLogPort, persistencePort);
        when(eventLogPort.existeEvento(anyString())).thenReturn(false);
        lenient().when(persistencePort.buscarPorId(anyString())).thenReturn(Optional.empty());
    }

    @Test @DisplayName("ORDER_CREATED crea snapshot con estado CREADO")
    void orderCreatedDebeCrearSnapshot() throws Exception {
        String orderId = UUID.randomUUID().toString();
        OrderStatusDTO dto = service.procesarEvento(envelopeOrderCreated(orderId));
        assertNotNull(dto);
        assertEquals(orderId, dto.orderId());
        assertEquals(OrderStatus.CREADO, dto.status());
    }

    @Test @DisplayName("ORDER_CREATED persiste evento y pedido")
    void orderCreatedDebePersistir() throws Exception {
        service.procesarEvento(envelopeOrderCreated(UUID.randomUUID().toString()));
        verify(eventLogPort,    times(1)).registrarEvento(any());
        verify(persistencePort, times(1)).guardarOActualizar(any());
    }

    @Test @DisplayName("DRIVER_ASSIGNED actualiza snapshot a ASIGNADO")
    void driverAssignedDebeActualizarEstado() throws Exception {
        String orderId = UUID.randomUUID().toString();
        service.procesarEvento(envelopeOrderCreated(orderId));
        resetMocks();
        OrderStatusDTO dto = service.procesarEvento(envelopeDriverAssigned(orderId));
        assertNotNull(dto);
        assertEquals(OrderStatus.ASIGNADO, dto.status());
    }

    @Test @DisplayName("DELIVERY_STARTED actualiza snapshot a EN_CAMINO")
    void deliveryStartedDebeActualizarEstado() throws Exception {
        String orderId = UUID.randomUUID().toString();
        service.procesarEvento(envelopeOrderCreated(orderId));
        resetMocks(); service.procesarEvento(envelopeDriverAssigned(orderId));
        resetMocks();
        OrderStatusDTO dto = service.procesarEvento(envelopeSimple(orderId, OrderEventType.DELIVERY_STARTED));
        assertEquals(OrderStatus.EN_CAMINO, dto.status());
    }

    @Test @DisplayName("ORDER_DELIVERED actualiza snapshot a ENTREGADO")
    void orderDeliveredDebeActualizarEstado() throws Exception {
        String orderId = UUID.randomUUID().toString();
        service.procesarEvento(envelopeOrderCreated(orderId));
        resetMocks(); service.procesarEvento(envelopeDriverAssigned(orderId));
        resetMocks(); service.procesarEvento(envelopeSimple(orderId, OrderEventType.DELIVERY_STARTED));
        resetMocks(); service.procesarEvento(envelopeSimple(orderId, OrderEventType.DRIVER_ARRIVING));
        resetMocks();
        OrderStatusDTO dto = service.procesarEvento(envelopeSimple(orderId, OrderEventType.ORDER_DELIVERED));
        assertEquals(OrderStatus.ENTREGADO, dto.status());
    }

    @Test @DisplayName("Evento duplicado debe ser ignorado (idempotencia)")
    void eventoDuplicadoDebeSerIgnorado() throws Exception {
        when(eventLogPort.existeEvento(anyString())).thenReturn(true);
        OrderStatusDTO dto = service.procesarEvento(envelopeOrderCreated(UUID.randomUUID().toString()));
        assertNull(dto);
        verify(persistencePort, never()).guardarOActualizar(any());
    }

    @Test @DisplayName("Mantiene snapshots de múltiples pedidos")
    void debeManitenerMultiplesSnapshots() throws Exception {
        service.procesarEvento(envelopeOrderCreated(UUID.randomUUID().toString()));
        resetMocks();
        service.procesarEvento(envelopeOrderCreated(UUID.randomUUID().toString()));
        assertEquals(2, service.obtenerTodosLosSnapshots().size());
    }
}
