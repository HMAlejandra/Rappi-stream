package com.rappistream.dashboardaggregator.infrastructure.persistence.repository;

import com.rappistream.dashboardaggregator.infrastructure.persistence.entity.OrderEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOrderEventRepository extends JpaRepository<OrderEventEntity, Long> {
    boolean existsByEventId(String eventId);
}
