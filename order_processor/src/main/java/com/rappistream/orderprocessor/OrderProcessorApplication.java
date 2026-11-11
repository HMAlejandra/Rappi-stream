package com.rappistream.orderprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del microservicio order_processor.
 *
 * Responsabilidad:
 * Consume OrderCreatedCommand de RabbitMQ (new_orders_queue),
 * aplica el dominio rico (Patrón State + Strategy),
 * persiste en PostgreSQL (JPA) y publica DRIVER_ASSIGNED en Kafka.
 *
 * Puerto: 8081
 */
@SpringBootApplication
public class OrderProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderProcessorApplication.class, args);
    }
}
