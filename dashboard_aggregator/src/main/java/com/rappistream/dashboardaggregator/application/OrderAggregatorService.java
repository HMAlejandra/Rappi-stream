package com.rappistream.dashboardaggregator.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappistream.common.dto.OrderStatusDTO;
import com.rappistream.common.enums.OrderEventType;
import com.rappistream.common.enums.OrderStatus;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.common.events.payloads.DriverAssignedPayload;
import com.rappistream.dashboardaggregator.domain.OrderSnapshot;
import com.rappistream.dashboardaggregator.infrastructure.persistence.entity.OrderEntity;
import com.rappistream.dashboardaggregator.infrastructure.persistence.entity.OrderEventEntity;
import com.rappistream.dashboardaggregator.infrastructure.persistence.repository.SpringDataOrderEventRepository;
import com.rappistream.dashboardaggregator.infrastructure.persistence.repository.SpringDataOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consumidor Stateful: mantiene una vista materializada en memoria de todos los pedidos.
 * Idempotencia — doble barrera:
 *   1. existsByEventId() antes de procesar (evita cómputo duplicado).
 *   2. UNIQUE en event_id en BD (protege contra race conditions concurrentes).
 */
@Service
public class OrderAggregatorService {

    private static final Logger log = LoggerFactory.getLogger(OrderAggregatorService.class);

    private final Map<String, OrderSnapshot> snapshots = new ConcurrentHashMap<>();

    private final SpringDataOrderRepository      orderRepo;
    private final SpringDataOrderEventRepository eventRepo;
    private final ObjectMapper                   objectMapper;

    public OrderAggregatorService(SpringDataOrderRepository orderRepo,
                                   SpringDataOrderEventRepository eventRepo,
                                   ObjectMapper objectMapper) {
        this.orderRepo    = orderRepo;
        this.eventRepo    = eventRepo;
        this.objectMapper = objectMapper;
    }

    public OrderStatusDTO procesarEvento(OrderEventEnvelope envelope) {
        if (eventRepo.existsByEventId(envelope.eventId())) {
            log.debug("[dashboard_aggregator] Evento ya procesado (idempotente) — eventId={}", envelope.eventId());
            return null;
        }
        try {
            OrderSnapshot snapshot = procesarEnMemoria(envelope);
            if (snapshot == null) return null;

            persistirEvento(envelope);
            persistirOrden(snapshot, envelope);

            log.info("[dashboard_aggregator] Evento procesado — orderId={} tipo={} nuevoEstado={}",
                    envelope.orderId(), envelope.eventType(), snapshot.getStatus());
            return toDTO(snapshot);

        } catch (DataIntegrityViolationException e) {
            log.debug("[dashboard_aggregator] Evento duplicado ignorado (race condition) — eventId={}", envelope.eventId());
            return null;
        } catch (Exception e) {
            log.error("[dashboard_aggregator] Error procesando evento — eventId={} orderId={}",
                    envelope.eventId(), envelope.orderId(), e);
            return null;
        }
    }

    private OrderSnapshot procesarEnMemoria(OrderEventEnvelope envelope) throws Exception {
        String orderId = envelope.orderId();

        switch (envelope.eventType()) {
            case ORDER_CREATED -> {
                if (!snapshots.containsKey(orderId)) {
                    var node        = objectMapper.readTree(envelope.payload());
                    String name     = node.path("customerName").asText("Desconocido");
                    String product  = node.path("product").asText("Desconocido");
                    int    quantity = node.path("quantity").asInt(1);
                    String address  = node.path("address").asText("");
                    snapshots.put(orderId, new OrderSnapshot(orderId, name, product, quantity, address));
                }
                return snapshots.get(orderId);
            }
            case DRIVER_ASSIGNED -> {
                OrderSnapshot snap = snapshots.computeIfAbsent(orderId,
                        id -> new OrderSnapshot(id, "Desconocido", "Desconocido", 1, ""));
                DriverAssignedPayload p = objectMapper.readValue(envelope.payload(), DriverAssignedPayload.class);
                snap.aplicarDriverAssigned(p.driverId(), p.driverName());
                return snap;
            }
            case DELIVERY_STARTED -> {
                OrderSnapshot snap = snapshots.computeIfAbsent(orderId,
                        id -> new OrderSnapshot(id, "Desconocido", "Desconocido", 1, ""));
                snap.aplicarDeliveryStarted();
                return snap;
            }
            case DRIVER_ARRIVING -> {
                OrderSnapshot snap = snapshots.computeIfAbsent(orderId,
                        id -> new OrderSnapshot(id, "Desconocido", "Desconocido", 1, ""));
                snap.aplicarDriverArriving();
                return snap;
            }
            case ORDER_DELIVERED -> {
                OrderSnapshot snap = snapshots.computeIfAbsent(orderId,
                        id -> new OrderSnapshot(id, "Desconocido", "Desconocido", 1, ""));
                snap.aplicarOrderDelivered();
                return snap;
            }
            default -> {
                log.debug("[dashboard_aggregator] Tipo de evento no manejado: {}", envelope.eventType());
                return null;
            }
        }
    }

    private void persistirEvento(OrderEventEnvelope envelope) {
        eventRepo.save(new OrderEventEntity(
                envelope.eventId(), envelope.orderId(), envelope.eventType().name(),
                envelope.timestamp(), envelope.payload()));
    }

    private void persistirOrden(OrderSnapshot snap, OrderEventEnvelope envelope) {
        orderRepo.findById(snap.getOrderId()).ifPresentOrElse(
            entity -> {
                entity.setStatus(snap.getStatus().name());
                if (snap.getDriverId() != null) entity.setDriverId(snap.getDriverId());
                entity.setUpdatedAt(Instant.now());
                orderRepo.save(entity);
            },
            () -> orderRepo.save(new OrderEntity(
                    snap.getOrderId(), snap.getCustomerName(), snap.getProduct(),
                    snap.getQuantity() > 0 ? snap.getQuantity() : 1,
                    snap.getAddress().isEmpty() ? "Sin dirección" : snap.getAddress(),
                    snap.getDriverId(), snap.getStatus().name(), snap.getCreadoEn(), Instant.now()))
        );
    }

    private OrderStatusDTO toDTO(OrderSnapshot snap) {
        return new OrderStatusDTO(snap.getOrderId(), snap.getCustomerName(), snap.getProduct(),
                snap.getQuantity(), snap.getAddress(), snap.getDriverId(), snap.getDriverName(),
                snap.getStatus(), snap.getLastUpdated());
    }

    public Collection<OrderSnapshot> obtenerTodosLosSnapshots() {
        return Collections.unmodifiableCollection(snapshots.values());
    }
}
