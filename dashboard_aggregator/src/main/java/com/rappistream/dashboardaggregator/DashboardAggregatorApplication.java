package com.rappistream.dashboardaggregator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio: dashboard_aggregator
 *
 * Responsabilidades:
 * 1. Consumir TODOS los eventos del topic order_events desde el principio.
 * 2. Reconstruir el estado actual de cada pedido en memoria (vista materializada).
 * 3. Persistir el estado en PostgreSQL de forma idempotente (event_id UNIQUE).
 * 4. Notificar al dashboard_backend vía HTTP para que haga WebSocket broadcast.
 *
 * Sistemas Distribuidos — Consumidor Stateful:
 * El groupId "dashboard-aggregator-group" con auto-offset-reset=earliest
 * garantiza que al arrancar por primera vez, procesa todos los eventos
 * históricos para reconstruir el estado completo desde cero.
 *
 * Idempotencia:
 * Si el servicio reinicia, reprocesa desde el principio pero los INSERTs
 * en order_events fallan silenciosamente por UNIQUE constraint en event_id.
 * Los UPDATEs en orders son idempotentes por naturaleza.
 */
@SpringBootApplication
public class DashboardAggregatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(DashboardAggregatorApplication.class, args);
    }
}
