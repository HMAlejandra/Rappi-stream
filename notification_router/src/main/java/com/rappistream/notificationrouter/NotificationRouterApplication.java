package com.rappistream.notificationrouter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del microservicio notification_router.
 *
 * Responsabilidad (Patrón Bridge):
 * Puente entre Kafka y RabbitMQ. Consume todos los eventos
 * del topic order_events, aplica Strategy polimórfica para
 * determinar si se requiere notificación y construye un
 * NotificationCommand que publica en el Topic Exchange de RabbitMQ.
 *
 * Eventos que disparan notificación:
 *   DRIVER_ARRIVING  → SMS al cliente  (routing key: notify.client.sms)
 *   ORDER_DELIVERED  → Push al repartidor (routing key: notify.driver.push)
 *
 * Puerto: 8083
 */
@SpringBootApplication
public class NotificationRouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationRouterApplication.class, args);
    }
}
