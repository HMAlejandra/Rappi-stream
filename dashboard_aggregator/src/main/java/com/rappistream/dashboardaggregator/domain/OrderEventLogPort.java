package com.rappistream.dashboardaggregator.domain;

import com.rappistream.common.events.OrderEventEnvelope;

/**
 * Puerto de salida para el log de auditoría de eventos procesados.
 * DIP + ISP: separado de OrderPersistencePort — responsabilidad única.
 */
public interface OrderEventLogPort {
    boolean existeEvento(String eventId);
    void registrarEvento(OrderEventEnvelope envelope);
}
