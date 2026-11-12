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
        List<Driver> disponibles = repositorio.obtenerRepartidoresDisponibles();
        log.info("[order_processor] DriverAssignmentStrategy inicializada con {} repartidores",
                disponibles.size());
        return new RoundRobinAssignmentStrategy(disponibles);
    }
}
