package com.rappistream.dashboardaggregator.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entidad JPA — tabla 'order_events'.
 *
 * Registra cada evento procesado por el aggregator.
 * event_id tiene restricción UNIQUE en la BD (creada por init.sql).
 *
 * Idempotencia:
 * Si el aggregator reprocesa un evento ya registrado,
 * el INSERT falla por la constraint UNIQUE. Se captura
 * la DataIntegrityViolationException y se ignora
 * silenciosamente (el evento ya fue procesado antes).
 */
@Entity
@Table(name = "order_events")
public class OrderEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 36, nullable = false, unique = true)
    private String eventId;

    @Column(name = "order_id", length = 36, nullable = false)
    private String orderId;

    @Column(name = "event_type", length = 50, nullable = false)
    private String eventType;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected OrderEventEntity() {}

    public OrderEventEntity(String eventId, String orderId, String eventType,
                             Instant eventTimestamp, String payload) {
        this.eventId        = eventId;
        this.orderId        = orderId;
        this.eventType      = eventType;
        this.eventTimestamp = eventTimestamp;
        this.payload        = payload;
        this.recordedAt     = Instant.now();
    }

    public Long    getId()             { return id; }
    public String  getEventId()        { return eventId; }
    public String  getOrderId()        { return orderId; }
    public String  getEventType()      { return eventType; }
    public Instant getEventTimestamp() { return eventTimestamp; }
    public String  getPayload()        { return payload; }
    public Instant getRecordedAt()     { return recordedAt; }
}
