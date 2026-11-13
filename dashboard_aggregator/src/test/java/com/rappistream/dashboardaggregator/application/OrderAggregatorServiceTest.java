package com.rappistream.dashboardaggregator.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rappistream.common.dto.OrderStatusDTO;
import com.rappistream.common.enums.OrderEventType;
import com.rappistream.common.enums.OrderStatus;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.common.events.payloads.DriverAssignedPayload;
import com.rappistream.dashboardaggregator.infrastructure.persistence.repository.SpringDataOrderEventRepository;
import com.rappistream.dashboardaggregator.infrastructure.persistence.repository.SpringDataOrderRepository;
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

/**
 * Pruebas unitarias del OrderAggregatorService.
 *
 * Estrategia:
 * - Repositorios JPA mockeados (sin BD real).
 * - ObjectMapper real (necesario para deserializar payloads JSON).
 * - Se verifica el estado en memoria y las llamadas a persistencia.
 * Patrón AAA.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderAggregatorService — Consumidor Stateful")
class OrderAggregatorServiceTest {

    @Mock
    private SpringDataOrderRepository      orderRepo;

    @Mock
    private SpringDataOrderEventRepository eventRepo;

    private ObjectMapper objectMapper;
    private OrderAggregatorService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        service = new OrderAggregatorService(orderRepo, eventRepo, objectMapper);

        // Por defecto: el evento no existe (no es duplicado)
        when(eventRepo.existsByEventId(anyString())).thenReturn(false);
        // Por defecto: el pedido no existe en BD (primera vez)
        when(orderRepo.findById(anyString())).thenReturn(Optional.empty());
    }

    private OrderEventEnvelope envelopeOrderCreated(String orderId) throws Exception {
        String payload = String.format(
                "{\"orderId\":\"%s\",\"customerName\":\"María García\",\"product\":\"Hamburguesa\"}",
                orderId);
        return new OrderEventEnvelope(UUID.randomUUID().toString(), orderId,
                OrderEventType.ORDER_CREATED, Instant.now(), payload);
    }

    private OrderEventEnvelope envelopeDriverAssigned(String orderId) throws Exception {
        DriverAssignedPayload payload = new DriverAssignedPayload("D-001", "Carlos Mendoza");
        String payloadJson = objectMapper.writeValueAsString(payload);
        return new OrderEventEnvelope(UUID.randomUUID().toString(), orderId,
                OrderEventType.DRIVER_ASSIGNED, Instant.now(), payloadJson);
    }

    private OrderEventEnvelope envelopeSimple(String orderId, OrderEventType tipo) {
        return new OrderEventEnvelope(UUID.randomUUID().toString(), orderId,
                tipo, Instant.now(), "{\"driverId\":\"D-001\"}");
    }

    // =========================================================
    // ORDER_CREATED
    // =========================================================

    @Test
    @DisplayName("ORDER_CREATED debe crear snapshot con estado CREADO")
    void orderCreatedDebeCrearSnapshot() throws Exception {
        String orderId = UUID.randomUUID().toString();
        OrderStatusDTO dto = service.procesarEvento(envelopeOrderCreated(orderId));

        assertNotNull(dto);
        assertEquals(orderId,          dto.orderId());
        assertEquals(OrderStatus.CREADO, dto.status());
        assertEquals("María García",   dto.customerName());
    }

    @Test
    @DisplayName("ORDER_CREATED debe persistir en BD")
    void orderCreatedDebePersistirEnBD() throws Exception {
        service.procesarEvento(envelopeOrderCreated(UUID.randomUUID().toString()));
        verify(eventRepo, times(1)).save(any());
        verify(orderRepo, times(1)).save(any());
    }

    // =========================================================
    // DRIVER_ASSIGNED
    // =========================================================

    @Test
    @DisplayName("DRIVER_ASSIGNED debe actualizar snapshot a ASIGNADO")
    void driverAssignedDebeActualizarEstado() throws Exception {
        String orderId = UUID.randomUUID().toString();
        // Primero crear el snapshot
        service.procesarEvento(envelopeOrderCreated(orderId));
        reset(eventRepo, orderRepo);
        when(eventRepo.existsByEventId(anyString())).thenReturn(false);
        when(orderRepo.findById(orderId)).thenReturn(Optional.empty());

        OrderStatusDTO dto = service.procesarEvento(envelopeDriverAssigned(orderId));

        assertNotNull(dto);
        assertEquals(OrderStatus.ASIGNADO, dto.status());
        assertEquals("D-001",             dto.driverId());
        assertEquals("Carlos Mendoza",    dto.driverName());
    }

    // =========================================================
    // DELIVERY_STARTED
    // =========================================================

    @Test
    @DisplayName("DELIVERY_STARTED debe actualizar snapshot a EN_CAMINO")
    void deliveryStartedDebeActualizarEstado() throws Exception {
        String orderId = UUID.randomUUID().toString();
        service.procesarEvento(envelopeOrderCreated(orderId));
        service.procesarEvento(envelopeDriverAssigned(orderId));
        when(orderRepo.findById(orderId)).thenReturn(Optional.empty());
        when(eventRepo.existsByEventId(anyString())).thenReturn(false);

        OrderStatusDTO dto = service.procesarEvento(
                envelopeSimple(orderId, OrderEventType.DELIVERY_STARTED));

        assertNotNull(dto);
        assertEquals(OrderStatus.EN_CAMINO, dto.status());
    }

    // =========================================================
    // DRIVER_ARRIVING
    // =========================================================

    @Test
    @DisplayName("DRIVER_ARRIVING debe actualizar snapshot a LLEGANDO")
    void driverArrivingDebeActualizarEstado() throws Exception {
        String orderId = UUID.randomUUID().toString();
        service.procesarEvento(envelopeOrderCreated(orderId));
        service.procesarEvento(envelopeDriverAssigned(orderId));
        when(eventRepo.existsByEventId(anyString())).thenReturn(false);
        when(orderRepo.findById(orderId)).thenReturn(Optional.empty());
        service.procesarEvento(envelopeSimple(orderId, OrderEventType.DELIVERY_STARTED));
        when(eventRepo.existsByEventId(anyString())).thenReturn(false);
        when(orderRepo.findById(orderId)).thenReturn(Optional.empty());

        OrderStatusDTO dto = service.procesarEvento(
                envelopeSimple(orderId, OrderEventType.DRIVER_ARRIVING));

        assertNotNull(dto);
        assertEquals(OrderStatus.LLEGANDO, dto.status());
    }

    // =========================================================
    // ORDER_DELIVERED
    // =========================================================

    @Test
    @DisplayName("ORDER_DELIVERED debe actualizar snapshot a ENTREGADO")
    void orderDeliveredDebeActualizarEstado() throws Exception {
        String orderId = UUID.randomUUID().toString();
        // Ciclo completo
        service.procesarEvento(envelopeOrderCreated(orderId));
        service.procesarEvento(envelopeDriverAssigned(orderId));
        when(eventRepo.existsByEventId(anyString())).thenReturn(false);
        when(orderRepo.findById(orderId)).thenReturn(Optional.empty());
        service.procesarEvento(envelopeSimple(orderId, OrderEventType.DELIVERY_STARTED));
        when(eventRepo.existsByEventId(anyString())).thenReturn(false);
        when(orderRepo.findById(orderId)).thenReturn(Optional.empty());
        service.procesarEvento(envelopeSimple(orderId, OrderEventType.DRIVER_ARRIVING));
        when(eventRepo.existsByEventId(anyString())).thenReturn(false);
        when(orderRepo.findById(orderId)).thenReturn(Optional.empty());

        OrderStatusDTO dto = service.procesarEvento(
                envelopeSimple(orderId, OrderEventType.ORDER_DELIVERED));

        assertNotNull(dto);
        assertEquals(OrderStatus.ENTREGADO, dto.status());
    }

    // =========================================================
    // IDEMPOTENCIA
    // =========================================================

    @Test
    @DisplayName("Evento duplicado (mismo eventId) debe ser ignorado")
    void eventoDuplicadoDebeSerIgnorado() throws Exception {
        // Arrange: el evento YA fue procesado (existsByEventId retorna true)
        // No configuramos orderRepo.findById porque el método retorna antes de llegar a él
        lenient().when(orderRepo.findById(anyString())).thenReturn(Optional.empty());
        when(eventRepo.existsByEventId(anyString())).thenReturn(true);

        // Act
        OrderStatusDTO dto = service.procesarEvento(
                envelopeOrderCreated(UUID.randomUUID().toString()));

        // Assert
        assertNull(dto, "Un evento duplicado debe retornar null");
        verify(orderRepo, never()).save(any());
        verify(eventRepo, never()).save(any());
    }

    @Test
    @DisplayName("El aggregator debe mantener snapshots de múltiples pedidos")
    void debeManitenerMultiplesSnapshots() throws Exception {
        String order1 = UUID.randomUUID().toString();
        String order2 = UUID.randomUUID().toString();

        service.procesarEvento(envelopeOrderCreated(order1));
        when(eventRepo.existsByEventId(anyString())).thenReturn(false);
        when(orderRepo.findById(anyString())).thenReturn(Optional.empty());
        service.procesarEvento(envelopeOrderCreated(order2));

        assertEquals(2, service.obtenerTodosLosSnapshots().size());
    }
}
