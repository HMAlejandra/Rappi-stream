package com.rappistream.dashboardaggregator.domain;

import java.util.Optional;

/**
 * Puerto de salida para persistir el estado consolidado de pedidos.
 * DIP: el dominio define este contrato. La infraestructura lo implementa.
 */
public interface OrderPersistencePort {
    Optional<OrderSnapshot> buscarPorId(String orderId);
    void guardarOActualizar(OrderSnapshot snapshot);
}
