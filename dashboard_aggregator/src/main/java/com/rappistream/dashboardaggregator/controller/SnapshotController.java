package com.rappistream.dashboardaggregator.controller;

import com.rappistream.common.dto.OrderStatusDTO;
import com.rappistream.common.enums.OrderStatus;
import com.rappistream.dashboardaggregator.service.OrderAggregatorService;
import com.rappistream.dashboardaggregator.domain.OrderSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controlador REST — expone el estado actual de todos los pedidos.
 *
 * SRP: solo gestiona la capa HTTP. No contiene lógica de agregación.
 * GET /api/snapshots → lista de OrderStatusDTO
 */
@RestController
@RequestMapping("/api")
public class SnapshotController {

    private final OrderAggregatorService aggregatorService;

    public SnapshotController(OrderAggregatorService aggregatorService) {
        this.aggregatorService = aggregatorService;
    }

    @GetMapping("/snapshots")
    public ResponseEntity<List<OrderStatusDTO>> obtenerSnapshots() {
        Collection<OrderSnapshot> snapshots = aggregatorService.obtenerTodosLosSnapshots();
        List<OrderStatusDTO> dtos = snapshots.stream()
                .map(snap -> new OrderStatusDTO(
                        snap.getOrderId(), snap.getCustomerName(), snap.getProduct(),
                        snap.getQuantity(), snap.getAddress(), snap.getDriverId(),
                        snap.getDriverName(),
                        snap.getStatus() != null ? snap.getStatus() : OrderStatus.CREADO,
                        snap.getLastUpdated()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
}
