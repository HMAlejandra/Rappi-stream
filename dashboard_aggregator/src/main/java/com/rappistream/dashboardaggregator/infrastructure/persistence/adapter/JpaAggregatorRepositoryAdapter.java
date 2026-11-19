package com.rappistream.dashboardaggregator.infrastructure.persistence.adapter;

import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.dashboardaggregator.domain.OrderSnapshot;
import com.rappistream.dashboardaggregator.domain.port.OrderEventLogPort;
import com.rappistream.dashboardaggregator.domain.port.OrderPersistencePort;
import com.rappistream.dashboardaggregator.infrastructure.persistence.entity.OrderEntity;
import com.rappistream.dashboardaggregator.infrastructure.persistence.entity.OrderEventEntity;
import com.rappistream.dashboardaggregator.infrastructure.persistence.repository.SpringDataOrderEventRepository;
import com.rappistream.dashboardaggregator.infrastructure.persistence.repository.SpringDataOrderRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Adaptador JPA que implementa los dos puertos de persistencia del aggregator.
 *
 * DIP — Inversión de Dependencias:
 * Esta clase vive en la capa de infraestructura y depende del dominio,
 * no al revés. OrderAggregatorService solo conoce las interfaces de puerto.
 *
 * SRP — Responsabilidad Única:
 * Centraliza toda la traducción entre el dominio (OrderSnapshot) y
 * las entidades JPA (OrderEntity, OrderEventEntity).
 */
@Component
public class JpaAggregatorRepositoryAdapter implements OrderPersistencePort, OrderEventLogPort {

    private final SpringDataOrderRepository      orderRepo;
    private final SpringDataOrderEventRepository eventRepo;

    public JpaAggregatorRepositoryAdapter(SpringDataOrderRepository orderRepo,
                                           SpringDataOrderEventRepository eventRepo) {
        this.orderRepo = orderRepo;
        this.eventRepo = eventRepo;
    }

    // =========================================================
    // OrderPersistencePort
    // =========================================================

    @Override
    public Optional<OrderSnapshot> buscarPorId(String orderId) {
        return orderRepo.findById(orderId).map(this::toSnapshot);
    }

    @Override
    public void guardarOActualizar(OrderSnapshot snapshot) {
        orderRepo.findById(snapshot.getOrderId()).ifPresentOrElse(
            entity -> {
                entity.setStatus(snapshot.getStatus().name());
                if (snapshot.getDriverId() != null) {
                    entity.setDriverId(snapshot.getDriverId());
                }
                entity.setUpdatedAt(Instant.now());
                orderRepo.save(entity);
            },
            () -> orderRepo.save(new OrderEntity(
                    snapshot.getOrderId(),
                    snapshot.getCustomerName(),
                    snapshot.getProduct(),
                    snapshot.getQuantity() > 0 ? snapshot.getQuantity() : 1,
                    snapshot.getAddress().isEmpty() ? "Sin dirección" : snapshot.getAddress(),
                    snapshot.getDriverId(),
                    snapshot.getStatus().name(),
                    snapshot.getCreadoEn(),
                    Instant.now()))
        );
    }

    // =========================================================
    // OrderEventLogPort
    // =========================================================

    @Override
    public boolean existeEvento(String eventId) {
        return eventRepo.existsByEventId(eventId);
    }

    @Override
    public void registrarEvento(OrderEventEnvelope envelope) {
        eventRepo.save(new OrderEventEntity(
                envelope.eventId(),
                envelope.orderId(),
                envelope.eventType().name(),
                envelope.timestamp(),
                envelope.payload()));
    }

    // =========================================================
    // Conversión de infraestructura → dominio
    // =========================================================

    private OrderSnapshot toSnapshot(OrderEntity entity) {
        OrderSnapshot snap = new OrderSnapshot(
                entity.getId(),
                entity.getCustomerName(),
                entity.getProduct(),
                entity.getQuantity(),
                entity.getAddress()
        );
        if (entity.getDriverId() != null) {
            snap.aplicarDriverAssigned(entity.getDriverId(), entity.getDriverId());
        }
        return snap;
    }
}
