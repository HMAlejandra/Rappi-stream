package com.rappistream.orderprocessor.repository;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA para PedidoEntity.
 * Solo se usa internamente desde JpaOrderRepositoryAdapter.
 */
public interface SpringDataPedidoRepository extends JpaRepository<PedidoEntity, String> {
}
