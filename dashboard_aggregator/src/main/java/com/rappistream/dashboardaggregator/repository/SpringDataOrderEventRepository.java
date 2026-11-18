package com.rappistream.dashboardaggregator.repository;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOrderEventRepository extends JpaRepository<OrderEventEntity, Long> {
    boolean existsByEventId(String eventId);
}
