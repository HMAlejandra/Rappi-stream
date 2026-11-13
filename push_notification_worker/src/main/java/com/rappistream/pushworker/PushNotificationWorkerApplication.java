package com.rappistream.pushworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del microservicio push_notification_worker.
 *
 * Responsabilidad:
 * Consume NotificationCommand de driver_push_queue (binding: notify.driver.*)
 * y simula el envío de notificación Push imprimiendo en consola.
 * Puede escalar horizontalmente — RabbitMQ balancea entre réplicas.
 *
 * Puerto: 8085
 */
@SpringBootApplication
public class PushNotificationWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PushNotificationWorkerApplication.class, args);
    }
}
