package com.rappistream.smsworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del microservicio sms_worker.
 *
 * Responsabilidad:
 * Consume NotificationCommand de client_sms_queue (binding: notify.client.*)
 * y simula el envío de SMS imprimiendo en consola.
 * Puede escalar horizontalmente — RabbitMQ balancea entre réplicas.
 *
 * Puerto: 8084
 */
@SpringBootApplication
public class SmsWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmsWorkerApplication.class, args);
    }
}
