package com.rappistream.orderapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del microservicio order_api.
 *
 * Responsabilidad:
 * Puerta de entrada del sistema. Expone POST /api/orders,
 * valida la solicitud con Bean Validation y publica
 * un OrderCreatedCommand en RabbitMQ (new_orders_queue).
 *
 * Puerto: 8080
 */
@SpringBootApplication
public class OrderApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApiApplication.class, args);
    }
}
