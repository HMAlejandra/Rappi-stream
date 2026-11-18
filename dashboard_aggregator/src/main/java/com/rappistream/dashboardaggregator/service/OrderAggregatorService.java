package com.rappistream.dashboardaggregator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappistream.common.dto.OrderStatusDTO;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.common.events.payloads.DriverAssignedPayload;
import com.rappistream.dashboardaggregator.domain.OrderEventLogPort;
import com.rappistream.dashboardaggregator.domain.OrderPersistencePort;
import com.rappistream.dashboardaggregator.domain.OrderSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio — Consumidor Stateful del dashboard_aggregator.
 *
 * Mantiene una vista materializada en memoria de todos los pedidos.
 * Los servicios orquestan, el dominio decide.
 *
 * DIP: depende solo de interfaces de puerto (OrderPersistencePort, OrderEventLogPort).
 *
 * Idempotencia — doble barrera:
 *   1. existeEvento() antes de procesar (evita cómputo duplicado).
 *   2. UNIQUE en event_id en BD (protege ante race conditions).
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

    public OrderStatusDTO procesarEvento(OrderEventEnvelope envelope) {
        if (eventLogPort.existeEvento(envelope.eventId())) {
            log.debug("[dashboard_aggregator] Evento duplicado ignorado — eventId={}", envelope.eventId());
            return null;
        }

        try {
            OrderSnapshot snapshot = procesarEnMemoria(envelope);
            if (snapshot == null) return null;

            eventLogPort.registrarEvento(envelope);
            persistencePort.guardarOActualizar(snapshot);

            log.info("[dashboard_aggregator] Evento procesado — orderId={} tipo={} estado={}",
                    envelope.orderId(), envelope.eventType(), snapshot.getStatus());

            return toDTO(snapshot);

        } catch (DataIntegrityViolationException e) {
            log.debug("[dashboard_aggregator] Race condition ignorada — eventId={}", envelope.eventId());
            return null;
        } catch (Exception e) {
            log.error("[dashboard_aggregator] Error procesando evento — eventId={}", envelope.eventId(), e);
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
                log.debug("[dashboard_aggregator] Tipo no manejado: {}", envelope.eventType());
                yield null;
            }
        };
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
