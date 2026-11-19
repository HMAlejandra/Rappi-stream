package com.rappistream.dashboardaggregator.domain.port;

import com.rappistream.dashboardaggregator.domain.OrderSnapshot;

import java.util.Optional;

/**
 * Puerto de salida para persistir el estado consolidado de pedidos.
 *
 * DIP — Inversión de Dependencias:
 * El dominio (OrderAggregatorService) define este contrato.
 * La infraestructura (JpaAggregatorRepositoryAdapter) lo implementa.
 *
 * El servicio de aplicación nunca importa clases JPA ni Spring Data.
 * Toda dependencia fluye en una sola dirección:
 * infraestructura → dominio (nunca al revés).
 */
public interface OrderPersistencePort {

    /**
     * Busca un pedido por su identificador.
     *
     * @param orderId UUID del pedido
     * @return Optional vacío si no existe en BD
     */
    Optional<OrderSnapshot> buscarPorId(String orderId);

    /**
     * Persiste o actualiza el estado consolidado de un pedido.
     *
     * @param snapshot el snapshot con el estado actual en memoria
     */
    void guardarOActualizar(OrderSnapshot snapshot);
}
