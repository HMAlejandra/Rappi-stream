package com.rappistream.dashboardaggregator.infrastructure.persistence.repository;

import com.rappistream.dashboardaggregator.infrastructure.persistence.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOrderRepository extends JpaRepository<OrderEntity, String> {
}
