package com.rappistream.notificationrouter.service;

import com.rappistream.common.commands.NotificationCommand;
import com.rappistream.common.enums.NotificationType;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.notificationrouter.config.RabbitMqConfig;
import com.rappistream.notificationrouter.strategy.NotificationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Servicio orquestador del bridge Kafka → RabbitMQ.
 *
 * POO — Patrón Strategy en acción:
 * Recibe una lista de todas las estrategias disponibles (inyectadas
 * por Spring). Para cada evento, itera sobre ellas y delega en la
 * que aplica. No conoce los detalles de cada notificación.
 *
 * Esto respeta el principio Open/Closed: añadir un nuevo tipo de
 * notificación solo requiere crear una nueva implementación de
 * NotificationStrategy — sin tocar este servicio.
 *
 * Routing:
 * SMS  → exchange "notifications" con routing key "notify.client.sms"
 * PUSH → exchange "notifications" con routing key "notify.driver.push"
 */
@Service
public class NotificationRouterService {

    private static final Logger log = LoggerFactory.getLogger(NotificationRouterService.class);

    private final List<NotificationStrategy> estrategias;
    private final RabbitTemplate rabbitTemplate;

    public NotificationRouterService(List<NotificationStrategy> estrategias,
                                      RabbitTemplate rabbitTemplate) {
        this.estrategias   = estrategias;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Procesa un evento del topic order_events.
     * Busca la estrategia que aplica, construye el NotificationCommand
     * y lo publica en el Topic Exchange de RabbitMQ.
     *
     * @param envelope el evento recibido de Kafka
     */
    public void procesarEvento(OrderEventEnvelope envelope) {
        for (NotificationStrategy estrategia : estrategias) {
            if (estrategia.aplica(envelope.eventType())) {

                Optional<NotificationCommand> comando = estrategia.construir(envelope);

                comando.ifPresent(cmd -> {
                    String routingKey = routingKeyPara(cmd.type());
                    rabbitTemplate.convertAndSend(
                            RabbitMqConfig.NOTIFICATIONS_EXCHANGE,
                            routingKey,
                            cmd
                    );
                    log.info("[notification_router] NotificationCommand publicado — " +
                                    "type={} orderId={} routingKey={}",
                            cmd.type(), cmd.orderId(), routingKey);
                });

                return; // una estrategia por evento
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
