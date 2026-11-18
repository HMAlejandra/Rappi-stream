package com.rappistream.notificationrouter.service;

import com.rappistream.common.commands.NotificationCommand;
import com.rappistream.common.enums.NotificationType;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.notificationrouter.config.RabbitMqConfig;
import com.rappistream.notificationrouter.domain.NotificationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Servicio orquestador del bridge Kafka → RabbitMQ.
 *
 * Los servicios orquestan, el dominio decide.
 * Aplica el Patrón Strategy: itera sobre las estrategias inyectadas
 * y delega en la que aplica. No conoce los detalles de cada notificación.
 *
 * OCP: añadir un nuevo canal solo requiere una nueva implementación
 * de NotificationStrategy, sin tocar este servicio.
 */
@Service
public class NotificationRouterService {

    private static final Logger log = LoggerFactory.getLogger(NotificationRouterService.class);

    private final List<NotificationStrategy> estrategias;
    private final RabbitTemplate rabbitTemplate;

    public NotificationRouterService(List<NotificationStrategy> estrategias,
                                      RabbitTemplate rabbitTemplate) {
        this.estrategias    = estrategias;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void procesarEvento(OrderEventEnvelope envelope) {
        for (NotificationStrategy estrategia : estrategias) {
            if (estrategia.aplica(envelope.eventType())) {

                Optional<NotificationCommand> comando = estrategia.construir(envelope);

                comando.ifPresent(cmd -> {
                    String routingKey = routingKeyPara(cmd.type());
                    rabbitTemplate.convertAndSend(
                            RabbitMqConfig.NOTIFICATIONS_EXCHANGE, routingKey, cmd);
                    log.info("[notification_router] Publicado — type={} orderId={} routing={}",
                            cmd.type(), cmd.orderId(), routingKey);
                });

                return;
            }
        }
        log.debug("[notification_router] Sin estrategia para eventType={} orderId={}",
                envelope.eventType(), envelope.orderId());
    }

    private String routingKeyPara(NotificationType type) {
        return switch (type) {
            case SMS  -> RabbitMqConfig.ROUTING_KEY_SMS;
            case PUSH -> RabbitMqConfig.ROUTING_KEY_PUSH;
        };
    }
}
