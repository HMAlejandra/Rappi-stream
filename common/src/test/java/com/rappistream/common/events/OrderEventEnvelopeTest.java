package com.rappistream.common.events;

import com.rappistream.common.enums.OrderEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias del record OrderEventEnvelope.
 * Verifica las validaciones Fail-Fast del contrato de eventos Kafka.
 */
@DisplayName("OrderEventEnvelope — Validación Fail-Fast")
class OrderEventEnvelopeTest {

    private final String validEventId   = UUID.randomUUID().toString();
    private final String validOrderId   = UUID.randomUUID().toString();
    private final OrderEventType validType = OrderEventType.DRIVER_ASSIGNED;
    private final Instant validTimestamp = Instant.now();
    private final String validPayload   = "{\"driverId\":\"D-001\",\"driverName\":\"Carlos\"}";

    @Test
    @DisplayName("Debe crear el evento correctamente con datos válidos")
    void debeCrearEventoConDatosValidos() {
        var event = new OrderEventEnvelope(validEventId, validOrderId,
                validType, validTimestamp, validPayload);

        assertEquals(validEventId,   event.eventId());
        assertEquals(validOrderId,   event.orderId());
        assertEquals(validType,      event.eventType());
        assertEquals(validTimestamp, event.timestamp());
        assertEquals(validPayload,   event.payload());
    }

    @Test
    @DisplayName("Debe rechazar eventId nulo")
    void debeRechazarEventIdNulo() {
        assertThrows(NullPointerException.class, () ->
                new OrderEventEnvelope(null, validOrderId, validType,
                        validTimestamp, validPayload));
    }

    @Test
    @DisplayName("Debe rechazar eventId vacío")
    void debeRechazarEventIdVacio() {
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new OrderEventEnvelope("", validOrderId, validType,
                        validTimestamp, validPayload));
        assertTrue(ex.getMessage().contains("eventId"));
    }

    @Test
    @DisplayName("Debe rechazar eventType nulo")
    void debeRechazarEventTypeNulo() {
        assertThrows(NullPointerException.class, () ->
                new OrderEventEnvelope(validEventId, validOrderId, null,
                        validTimestamp, validPayload));
    }

    @Test
    @DisplayName("Debe rechazar payload vacío")
    void debeRechazarPayloadVacio() {
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new OrderEventEnvelope(validEventId, validOrderId, validType,
                        validTimestamp, "  "));
        assertTrue(ex.getMessage().contains("payload"));
    }
}
