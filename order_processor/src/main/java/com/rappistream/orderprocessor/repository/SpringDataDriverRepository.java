package com.rappistream.orderprocessor.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Repositorio Spring Data JPA para DriverEntity.
 * Usado por JpaOrderRepositoryAdapter para obtener repartidores disponibles.
 */
public interface SpringDataDriverRepository extends JpaRepository<DriverEntity, String> {

    /** Spring Data genera la query por el nombre del método. */
    List<DriverEntity> findByAvailableTrue();
}
