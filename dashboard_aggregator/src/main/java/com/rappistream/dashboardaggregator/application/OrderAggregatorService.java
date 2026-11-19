package com.rappistream.dashboardaggregator.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappistream.common.dto.OrderStatusDTO;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.common.events.payloads.DriverAssignedPayload;
import com.rappistream.dashboardaggregator.domain.OrderSnapshot;
import com.rappistream.dashboardaggregator.domain.port.OrderEventLogPort;
import com.rappistream.dashboardaggregator.domain.port.OrderPersistencePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de aplicación — Consumidor Stateful del dashboard_aggregator.
 *
 * Mantiene una vista materializada en memoria de todos los pedidos.
 * Cada evento de Kafka actualiza el snapshot correspondiente y persiste
 * el nuevo estado a través de los puertos de dominio.
 *
 * DIP — Inversión de Dependencias:
 * Este servicio depende únicamente de interfaces de puerto (OrderPersistencePort,
 * OrderEventLogPort). No importa ninguna clase de infraestructura (JPA, Spring Data).
 *
 * Idempotencia — doble barrera:
 *   1. existeEvento() antes de procesar (evita cómputo duplicado).
 *   2. UNIQUE en event_id en BD (protege contra race conditions concurrentes).
 */
@Service
public class OrderAggregatorService {

    private static final Logger log = LoggerFactory.getLogger(OrderAggregatorService.class);

    private final Map<String, OrderSnapshot> snapshots = new ConcurrentHashMap<>();

    private final OrderPersistencePort persistencePort;
    private final OrderEventLogPort    eventLogPort;
    private final ObjectMapper         objectMapper;

    public OrderAggregatorService(OrderPersistencePort persistencePort,
                                   OrderEventLogPort eventLogPort,
                                   ObjectMapper objectMapper) {
        this.persistencePort = persistencePort;
        this.eventLogPort    = eventLogPort;
        this.objectMapper    = objectMapper;
    }

    /**
     * Procesa un evento del topic order_events.
     * Actualiza el snapshot en memoria, persiste el estado y registra el evento.
     *
     * @param envelope el evento recibido de Kafka
     * @return OrderStatusDTO con el estado actualizado, o null si es duplicado
     */
    public OrderStatusDTO procesarEvento(OrderEventEnvelope envelope) {
        if (eventLogPort.existeEvento(envelope.eventId())) {
            log.debug("[dashboard_aggregator] Evento ya procesado (idempotente) — eventId={}",
                    envelope.eventId());
            return null;
        }

        try {
            OrderSnapshot snapshot = procesarEnMemoria(envelope);
            if (snapshot == null) return null;

            eventLogPort.registrarEvento(envelope);
            persistencePort.guardarOActualizar(snapshot);

            log.info("[dashboard_aggregator] Evento procesado — orderId={} tipo={} nuevoEstado={}",
                    envelope.orderId(), envelope.eventType(), snapshot.getStatus());

            return toDTO(snapshot);

        } catch (DataIntegrityViolationException e) {
            log.debug("[dashboard_aggregator] Evento duplicado ignorado (race condition) — eventId={}",
                    envelope.eventId());
            return null;
        } catch (Exception e) {
            log.error("[dashboard_aggregator] Error procesando evento — eventId={} orderId={}",
                    envelope.eventId(), envelope.orderId(), e);
            return null;
        }
    }

    private OrderSnapshot procesarEnMemoria(OrderEventEnvelope envelope) throws Exception {
        String orderId = envelope.orderId();

        return switch (envelope.eventType()) {
            case ORDER_CREATED -> {
                if (!snapshots.containsKey(orderId)) {
                    var node       = objectMapper.readTree(envelope.payload());
                    String name    = node.path("customerName").asText("Desconocido");
                    String product = node.path("product").asText("Desconocido");
                    int quantity   = node.path("quantity").asInt(1);
                    String address = node.path("address").asText("");
                    snapshots.put(orderId, new OrderSnapshot(orderId, name, product, quantity, address));
                }
                yield snapshots.get(orderId);
            }
            case DRIVER_ASSIGNED -> {
                OrderSnapshot snap = snapshots.computeIfAbsent(orderId,
                        id -> new OrderSnapshot(id, "Desconocido", "Desconocido", 1, ""));
                DriverAssignedPayload p =
                        objectMapper.readValue(envelope.payload(), DriverAssignedPayload.class);
                snap.aplicarDriverAssigned(p.driverId(), p.driverName());
                yield snap;
            }
            case DELIVERY_STARTED -> {
                OrderSnapshot snap = snapshots.computeIfAbsent(orderId,
                        id -> new OrderSnapshot(id, "Desconocido", "Desconocido", 1, ""));
                snap.aplicarDeliveryStarted();
                yield snap;
            }
            case DRIVER_ARRIVING -> {
                OrderSnapshot snap = snapshots.computeIfAbsent(orderId,
                        id -> new OrderSnapshot(id, "Desconocido", "Desconocido", 1, ""));
                snap.aplicarDriverArriving();
                yield snap;
            }
            case ORDER_DELIVERED -> {
                OrderSnapshot snap = snapshots.computeIfAbsent(orderId,
                        id -> new OrderSnapshot(id, "Desconocido", "Desconocido", 1, ""));
                snap.aplicarOrderDelivered();
                yield snap;
            }
            default -> {
                log.debug("[dashboard_aggregator] Tipo de evento no manejado: {}", envelope.eventType());
                yield null;
            }
        };
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

    public Collection<OrderSnapshot> obtenerTodosLosSnapshots() {
        return Collections.unmodifiableCollection(snapshots.values());
    }
}
