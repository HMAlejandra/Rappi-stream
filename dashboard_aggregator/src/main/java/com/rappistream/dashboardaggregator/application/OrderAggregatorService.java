package com.rappistream.dashboardaggregator.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappistream.common.dto.OrderStatusDTO;
import com.rappistream.common.enums.OrderEventType;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.common.events.payloads.DeliveryStartedPayload;
import com.rappistream.common.events.payloads.DriverArrivingPayload;
import com.rappistream.common.events.payloads.DriverAssignedPayload;
import com.rappistream.common.events.payloads.OrderDeliveredPayload;
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
 * Servicio Stateful que reconstruye y mantiene el estado
 * de todos los pedidos en memoria.
 *
 * Sistemas Distribuidos — Consumidor Stateful:
 * Mantiene un ConcurrentHashMap<orderId, OrderSnapshot> que
 * representa la vista materializada del sistema. Cada evento
 * de Kafka actualiza el snapshot correspondiente.
 *
 * ConcurrentHashMap: thread-safe para accesos concurrentes
 * del listener Kafka y posibles lecturas del endpoint HTTP.
 *
 * Idempotencia — Doble barrera:
 * 1. existsByEventId() antes de procesar (evita cómputo duplicado)
 * 2. DataIntegrityViolationException al insertar (si dos hilos
 *    procesan el mismo evento simultáneamente, solo uno gana)
 *
 * SRP: este servicio solo gestiona el estado en memoria y la
 * persistencia. La notificación al backend la hace el listener.
 */
@Service
public class OrderAggregatorService {

    private static final Logger log = LoggerFactory.getLogger(OrderAggregatorService.class);

    // Vista materializada en memoria — thread-safe
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

    /**
     * Procesa un evento del log de Kafka y actualiza el estado del sistema.
     *
     * @param envelope el evento recibido
     * @return el DTO con el estado actualizado, o null si el evento fue ignorado
     */
    public OrderStatusDTO procesarEvento(OrderEventEnvelope envelope) {
        // --- Idempotencia: verificar si ya procesamos este evento ---
        if (eventRepo.existsByEventId(envelope.eventId())) {
            log.debug("[dashboard_aggregator] Evento ya procesado (idempotente) — eventId={}",
                    envelope.eventId());
            return null;
        }

        try {
            OrderSnapshot snapshot = procesarEnMemoria(envelope);
            if (snapshot == null) return null;

            // Persistir el evento en order_events (auditoría)
            persistirEvento(envelope);

            // Persistir/actualizar el estado en orders
            persistirOrden(snapshot, envelope);

            log.info("[dashboard_aggregator] Evento procesado — orderId={} tipo={} nuevoEstado={}",
                    envelope.orderId(), envelope.eventType(), snapshot.getStatus());

            return toDTO(snapshot);

        } catch (DataIntegrityViolationException e) {
            // Raza entre dos hilos procesando el mismo evento — ignorar silenciosamente
            log.debug("[dashboard_aggregator] Evento duplicado ignorado (race condition) — eventId={}",
                    envelope.eventId());
            return null;
        } catch (Exception e) {
            log.error("[dashboard_aggregator] Error procesando evento — eventId={} orderId={}",
                    envelope.eventId(), envelope.orderId(), e);
            return null;
        }
    }

    /**
     * Actualiza la vista materializada en memoria según el tipo de evento.
     * @return el snapshot actualizado, o null si el evento no es relevante
     */
    private OrderSnapshot procesarEnMemoria(OrderEventEnvelope envelope) throws Exception {
        String orderId = envelope.orderId();

        switch (envelope.eventType()) {

            case ORDER_CREATED -> {
                // Crear snapshot inicial — parsear datos básicos del payload
                if (!snapshots.containsKey(orderId)) {
                    // El payload de ORDER_CREATED contiene customerName, product
                    // (formato simple de JSON generado en KafkaOrderEventPublisher)
                    var node = objectMapper.readTree(envelope.payload());
                    String customerName = node.path("customerName").asText("Desconocido");
                    String product      = node.path("product").asText("Desconocido");
                    // quantity y address los actualizamos cuando llegue DRIVER_ASSIGNED
                    OrderSnapshot snap = new OrderSnapshot(orderId, customerName, product, 0, "");
                    snapshots.put(orderId, snap);
                    log.debug("[dashboard_aggregator] Snapshot creado — orderId={}", orderId);
                }
                return snapshots.get(orderId);
            }

            case DRIVER_ASSIGNED -> {
                OrderSnapshot snap = snapshots.computeIfAbsent(orderId,
                        id -> new OrderSnapshot(id, "Desconocido", "Desconocido", 1, ""));
                DriverAssignedPayload payload =
                        objectMapper.readValue(envelope.payload(), DriverAssignedPayload.class);
                snap.aplicarDriverAssigned(payload.driverId(), payload.driverName());
                return snap;
            }

            case DELIVERY_STARTED -> {
                OrderSnapshot snap = snapshots.get(orderId);
                if (snap == null) {
                    log.warn("[dashboard_aggregator] DELIVERY_STARTED sin snapshot previo — orderId={}", orderId);
                    return null;
                }
                snap.aplicarDeliveryStarted();
                return snap;
            }

            case DRIVER_ARRIVING -> {
                OrderSnapshot snap = snapshots.get(orderId);
                if (snap == null) {
                    log.warn("[dashboard_aggregator] DRIVER_ARRIVING sin snapshot previo — orderId={}", orderId);
                    return null;
                }
                snap.aplicarDriverArriving();
                return snap;
            }

            case ORDER_DELIVERED -> {
                OrderSnapshot snap = snapshots.get(orderId);
                if (snap == null) {
                    log.warn("[dashboard_aggregator] ORDER_DELIVERED sin snapshot previo — orderId={}", orderId);
                    return null;
                }
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
        OrderEventEntity eventEntity = new OrderEventEntity(
                envelope.eventId(),
                envelope.orderId(),
                envelope.eventType().name(),
                envelope.timestamp(),
                envelope.payload()
        );
        eventRepo.save(eventEntity);
    }

    private void persistirOrden(OrderSnapshot snap, OrderEventEnvelope envelope) {
        orderRepo.findById(snap.getOrderId()).ifPresentOrElse(
            entity -> {
                // Actualizar el pedido existente
                entity.setStatus(snap.getStatus().name());
                if (snap.getDriverId() != null) {
                    entity.setDriverId(snap.getDriverId());
                }
                entity.setUpdatedAt(Instant.now());
                orderRepo.save(entity);
            },
            () -> {
                // Primera vez que vemos este pedido (ORDER_CREATED)
                OrderEntity entity = new OrderEntity(
                        snap.getOrderId(),
                        snap.getCustomerName(),
                        snap.getProduct(),
                        snap.getQuantity() > 0 ? snap.getQuantity() : 1,
                        snap.getAddress().isEmpty() ? "Sin dirección" : snap.getAddress(),
                        snap.getDriverId(),
                        snap.getStatus().name(),
                        snap.getCreadoEn(),
                        Instant.now()
                );
                orderRepo.save(entity);
            }
        );
    }

    private OrderStatusDTO toDTO(OrderSnapshot snap) {
        return new OrderStatusDTO(
                snap.getOrderId(),
                snap.getCustomerName(),
                snap.getProduct(),
                snap.getQuantity(),
                snap.getAddress(),
                snap.getDriverId(),
                snap.getDriverName(),
                snap.getStatus(),
                snap.getLastUpdated()
        );
    }

    /**
     * Retorna todos los snapshots actuales (para el endpoint de estado inicial).
     */
    public Collection<OrderSnapshot> obtenerTodosLosSnapshots() {
        return Collections.unmodifiableCollection(snapshots.values());
    }
}
