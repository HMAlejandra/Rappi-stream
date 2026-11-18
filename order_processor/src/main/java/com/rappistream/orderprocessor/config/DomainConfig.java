package com.rappistream.orderprocessor.config;

import com.rappistream.orderprocessor.domain.Driver;
import com.rappistream.orderprocessor.domain.DriverAssignmentStrategy;
import com.rappistream.orderprocessor.repository.OrderRepositoryPort;
import com.rappistream.orderprocessor.service.RoundRobinAssignmentStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Registra los beans del núcleo de negocio.
 *
 * DIP en acción: la capa de config crea e inyecta las implementaciones
 * concretas de los puertos del dominio. El dominio no sabe cómo se instancian.
 */
@Configuration
public class DomainConfig {

    private static final Logger log = LoggerFactory.getLogger(DomainConfig.class);

    @Bean
    public DriverAssignmentStrategy driverAssignmentStrategy(OrderRepositoryPort repositorio) {
        List<Driver> repartidores = repositorio.obtenerRepartidoresDisponibles();
        if (repartidores.isEmpty()) {
            log.warn("[order_processor] No se encontraron repartidores en BD. Usando semilla.");
            repartidores = List.of(
                    new Driver("D-001", "Carlos Mendoza",  true),
                    new Driver("D-002", "Laura Rodriguez", true),
                    new Driver("D-003", "Andres Torres",   true),
                    new Driver("D-004", "Valentina Gomez", true),
                    new Driver("D-005", "Miguel Herrera",  true)
            );
        }
        log.info("[order_processor] DriverAssignmentStrategy inicializada con {} repartidores",
                repartidores.size());
        return new RoundRobinAssignmentStrategy(repartidores);
    }
}
