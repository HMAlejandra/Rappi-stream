package com.rappistream.dashboardaggregator.repository;

import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.dashboardaggregator.domain.OrderEventLogPort;
import com.rappistream.dashboardaggregator.domain.OrderPersistencePort;
import com.rappistream.dashboardaggregator.domain.OrderSnapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Adaptador JPA — implementa OrderPersistencePort y OrderEventLogPort.
 *
 * DIP: vive en repository (infraestructura), depende del dominio, no al revés.
 * SRP: centraliza toda la traducción entre dominio ↔ entidades JPA.
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

    @Override
    public Optional<OrderSnapshot> buscarPorId(String orderId) {
        return orderRepo.findById(orderId).map(this::toSnapshot);
    }

    @Override
    public void guardarOActualizar(OrderSnapshot snapshot) {
        orderRepo.findById(snapshot.getOrderId()).ifPresentOrElse(
            entity -> {
                entity.setStatus(snapshot.getStatus().name());
                if (snapshot.getDriverId() != null) entity.setDriverId(snapshot.getDriverId());
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

    @Override
    public boolean existeEvento(String eventId) {
        return eventRepo.existsByEventId(eventId);
    }

    @Override
    public void registrarEvento(OrderEventEnvelope envelope) {
        eventRepo.save(new OrderEventEntity(
                envelope.eventId(), envelope.orderId(),
                envelope.eventType().name(), envelope.timestamp(), envelope.payload()));
    }

    private OrderSnapshot toSnapshot(OrderEntity entity) {
        OrderSnapshot snap = new OrderSnapshot(entity.getId(), entity.getCustomerName(),
                entity.getProduct(), entity.getQuantity(), entity.getAddress());
        if (entity.getDriverId() != null)
            snap.aplicarDriverAssigned(entity.getDriverId(), entity.getDriverId());
        return snap;
    }
}
