package com.rappistream.dashboardbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio: dashboard_backend
 *
 * Responsabilidades:
 * 1. Servir dashboard.html como aplicación web estática.
 * 2. Mantener conexiones WebSocket con los navegadores.
 * 3. Recibir actualizaciones del dashboard_aggregator via POST /internal/update.
 * 4. Hacer broadcast del estado actualizado a todos los clientes WebSocket.
 *
 * Puerto: 8087
 */
@SpringBootApplication
public class DashboardBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(DashboardBackendApplication.class, args);
    }
}
