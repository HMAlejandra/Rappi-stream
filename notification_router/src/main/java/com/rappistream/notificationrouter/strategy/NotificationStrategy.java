package com.rappistream.notificationrouter.strategy;

import com.rappistream.common.enums.OrderEventType;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.common.commands.NotificationCommand;

import java.util.Optional;

/**
 * Puerto Strategy para construir notificaciones a partir de eventos Kafka.
 *
 * POO — Patrón Strategy:
 * Cada implementación concreta sabe cómo reaccionar ante un tipo
 * de evento específico. NotificationRouterService itera sobre todas
 * las estrategias disponibles y delega en la que aplica.
 *
 * Esto elimina los if-else o switch sobre el tipo de evento
 * en el servicio: cada estrategia encapsula su propia lógica.
 */
public interface NotificationStrategy {

    /**
     * Indica si esta estrategia aplica para el tipo de evento dado.
     *
     * @param eventType el tipo del evento recibido de Kafka
     * @return true si esta estrategia debe manejar el evento
     */
    boolean aplica(OrderEventType eventType);

    /**
     * Construye el NotificationCommand a partir del envelope.
     * Solo se llama si aplica() retornó true.
     *
     * @param envelope el evento completo con payload JSON
     * @return Optional con el comando a publicar, o vacío si no aplica
     */
    Optional<NotificationCommand> construir(OrderEventEnvelope envelope);
}
