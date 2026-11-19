package com.rappistream.dashboardaggregator.domain.port;

import com.rappistream.common.events.OrderEventEnvelope;

/**
 * Puerto de salida para el log de auditoría de eventos procesados.
 *
 * DIP — Inversión de Dependencias:
 * El dominio define este contrato de escritura en el log de eventos.
 * La infraestructura (JpaAggregatorRepositoryAdapter) lo implementa
 * usando Spring Data JPA sobre la tabla order_events.
 *
 * ISP — Segregación de Interfaces:
 * Este puerto solo maneja el log de eventos.
 * No mezcla responsabilidades con la persistencia de pedidos.
 */
public interface OrderEventLogPort {

    /**
     * Verifica si un evento ya fue procesado (idempotencia).
     *
     * @param eventId UUID del evento
     * @return true si el evento ya existe en el log
     */
    boolean existeEvento(String eventId);

    /**
     * Registra un evento en el log de auditoría.
     *
     * @param envelope el evento a registrar
     */
    void registrarEvento(OrderEventEnvelope envelope);
}
