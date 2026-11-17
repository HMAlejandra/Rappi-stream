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
 * Configuración del dominio — registra los beans del núcleo de negocio.
 *
 * DIP en acción:
 * La capa de infraestructura crea e inyecta las implementaciones concretas
 * de los puertos del dominio. El dominio no sabe cómo se instancian.
 *
 * RoundRobinAssignmentStrategy necesita la lista de repartidores disponibles
 * que se obtiene del repositorio al arrancar. Se carga una vez al inicio.
 * Si la BD aún no tiene datos se usa una lista de repartidores por defecto (semilla).
 */
@Configuration
public class DomainConfig {

    private static final Logger log = LoggerFactory.getLogger(DomainConfig.class);

    /**
     * Registra la estrategia de asignación de repartidores como bean de Spring.
     * Carga los repartidores disponibles desde la base de datos al arrancar.
     */
    @Bean
    public DriverAssignmentStrategy driverAssignmentStrategy(OrderRepositoryPort repositorio) {
        List<Driver> repartidores = repositorio.obtenerRepartidoresDisponibles();
        if (repartidores.isEmpty()) {
            log.warn("[order_processor] No se encontraron repartidores en BD. " +
                     "Usando lista de repartidores por defecto (semilla).");
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
