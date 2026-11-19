package com.rappistream.notificationrouter.service.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappistream.common.commands.NotificationCommand;
import com.rappistream.common.enums.NotificationType;
import com.rappistream.common.enums.OrderEventType;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.common.events.payloads.OrderDeliveredPayload;
import com.rappistream.notificationrouter.service.NotificationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Estrategia concreta: ORDER_DELIVERED â†’ Push al repartidor.
 *
 * POO â€” PatrÃ³n Strategy (implementaciÃ³n concreta):
 * Cuando el pedido es entregado, construye un NotificationCommand
 * de tipo PUSH dirigido al repartidor que realizÃ³ la entrega.
 *
 * Routing: notify.driver.push â†’ driver_push_queue â†’ push_notification_worker
 */
@Component
public class OrderDeliveredPushStrategy implements NotificationStrategy {

    private static final Logger log = LoggerFactory.getLogger(OrderDeliveredPushStrategy.class);

    private final ObjectMapper objectMapper;

    public OrderDeliveredPushStrategy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean aplica(OrderEventType eventType) {
        return eventType == OrderEventType.ORDER_DELIVERED;
    }

    @Override
    public Optional<NotificationCommand> construir(OrderEventEnvelope envelope) {
        try {
            OrderDeliveredPayload payload =
                    objectMapper.readValue(envelope.payload(), OrderDeliveredPayload.class);

            String mensaje = String.format(
                    "Â¡Entrega completada! Pedido %s entregado exitosamente. Â¡Buen trabajo, %s!",
                    envelope.orderId(), payload.driverName()
            );

            NotificationCommand comando = new NotificationCommand(
                    UUID.randomUUID().toString(),
                    envelope.orderId(),
                    payload.driverId(),         // recipientId = driverId
                    NotificationType.PUSH,
                    mensaje,
                    Instant.now()
            );

            log.info("[notification_router] Push preparado â€” orderId={} driverId={}",
                    envelope.orderId(), payload.driverId());
            return Optional.of(comando);

        } catch (Exception e) {
            log.error("[notification_router] Error construyendo Push â€” orderId={}",
                    envelope.orderId(), e);
            return Optional.empty();
        }
    }
}

