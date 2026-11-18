package com.rappistream.notificationrouter.domain;

import com.rappistream.common.commands.NotificationCommand;
import com.rappistream.common.enums.OrderEventType;
import com.rappistream.common.events.OrderEventEnvelope;

import java.util.Optional;

/**
 * Contrato del Patrón Strategy para construir notificaciones.
 *
 * POO — Strategy + OCP:
 * Cada implementación concreta sabe reaccionar ante un tipo de evento.
 * NotificationRouterService itera y delega — sin if-else sobre tipos.
 * Para agregar un nuevo canal (email) basta crear una nueva clase.
 */
public interface NotificationStrategy {

    boolean aplica(OrderEventType eventType);
    Optional<NotificationCommand> construir(OrderEventEnvelope envelope);
}
