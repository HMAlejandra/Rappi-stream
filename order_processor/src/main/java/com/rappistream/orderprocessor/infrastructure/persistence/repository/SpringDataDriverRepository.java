package com.rappistream.orderprocessor.infrastructure.persistence.repository;

import com.rappistream.orderprocessor.infrastructure.persistence.entity.DriverEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositorio Spring Data JPA para DriverEntity.
 * Usado por JpaOrderRepositoryAdapter para obtener repartidores disponibles.
 */
public interface SpringDataDriverRepository extends JpaRepository<DriverEntity, String> {

    /**
     * Retorna todos los repartidores con available = true.
     * Spring Data genera la query automáticamente por el nombre del método.
     */
    List<DriverEntity> findByAvailableTrue();
}
