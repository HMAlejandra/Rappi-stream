package com.rappistream.orderprocessor.infrastructure.persistence.repository;

import com.rappistream.orderprocessor.infrastructure.persistence.entity.PedidoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA para PedidoEntity.
 * Solo se usa internamente desde JpaOrderRepositoryAdapter.
 * El dominio nunca ve esta interfaz.
 */
public interface SpringDataPedidoRepository extends JpaRepository<PedidoEntity, String> {
}
