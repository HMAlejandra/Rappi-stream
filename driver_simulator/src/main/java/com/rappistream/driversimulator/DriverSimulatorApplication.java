package com.rappistream.driversimulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Punto de entrada del microservicio driver_simulator.
 *
 * Responsabilidad:
 * Escucha eventos DRIVER_ASSIGNED en el topic order_events de Kafka
 * y simula el ciclo de entrega publicando secuencialmente:
 *   1. DELIVERY_STARTED
 *   2. DRIVER_ARRIVING
 *   3. ORDER_DELIVERED
 *
 * @EnableAsync habilita el procesamiento asíncrono de @Async en
 * DeliverySimulatorService, para no bloquear el hilo del consumidor
 * de Kafka durante los delays de simulación.
 *
 * Puerto: 8082 (solo para healthcheck — no expone API de negocio)
 */
@SpringBootApplication
@EnableAsync
public class DriverSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(DriverSimulatorApplication.class, args);
    }
}
