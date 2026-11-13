package com.rappistream.orderprocessor.infrastructure.config;

import com.rappistream.orderprocessor.domain.model.Driver;
import com.rappistream.orderprocessor.domain.port.OrderRepositoryPort;
import com.rappistream.orderprocessor.domain.strategy.DriverAssignmentStrategy;
import com.rappistream.orderprocessor.domain.strategy.RoundRobinAssignmentStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuración del dominio — registra los beans de estrategia.
 *
 * DIP: Spring inyecta DriverAssignmentStrategy (interfaz) en
 * OrderProcessorService. Esta clase decide qué implementación
 * concreta usar (RoundRobin) sin que el servicio lo sepa.
 *
 * La lista de repartidores se carga desde PostgreSQL al arrancar
 * via OrderRepositoryPort para mantener el dominio desacoplado de JPA.
 */
@Configuration
public class DomainConfig {

    private static final Logger log = LoggerFactory.getLogger(DomainConfig.class);

    @Bean
    public DriverAssignmentStrategy driverAssignmentStrategy(OrderRepositoryPort repositorio) {
        List<Driver> repartidores = repositorio.obtenerRepartidoresDisponibles();

        if (repartidores.isEmpty()) {
            log.warn("[order_processor] No se encontraron repartidores en BD. " +
                     "Usando lista de repartidores por defecto (semilla).");
            // Fallback: repartidores por defecto si la BD aún no tiene datos
            repartidores = List.of(
                    new Driver("D-001", "Carlos Mendoza",   true),
                    new Driver("D-002", "Laura Rodriguez",  true),
                    new Driver("D-003", "Andres Torres",    true),
                    new Driver("D-004", "Valentina Gomez",  true),
                    new Driver("D-005", "Miguel Herrera",   true)
            );
        }

        log.info("[order_processor] DriverAssignmentStrategy inicializada con {} repartidores",
                repartidores.size());
        return new RoundRobinAssignmentStrategy(repartidores);
    }
}
