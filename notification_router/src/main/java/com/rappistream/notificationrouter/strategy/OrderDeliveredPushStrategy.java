package com.rappistream.notificationrouter.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappistream.common.commands.NotificationCommand;
import com.rappistream.common.enums.NotificationType;
import com.rappistream.common.enums.OrderEventType;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.common.events.payloads.OrderDeliveredPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Estrategia para ORDER_DELIVERED → Push al repartidor.
 *
 * Cuando el pedido es entregado, se notifica al repartidor
 * vía Push confirmando la entrega exitosa.
 * Routing key: notify.driver.push → driver_push_queue → push_notification_worker
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
                    "¡Entrega completada! Pedido %s entregado exitosamente. ¡Buen trabajo, %s!",
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

            log.info("[notification_router] Push preparado — orderId={} driverId={}",
                    envelope.orderId(), payload.driverId());

            return Optional.of(comando);

        } catch (Exception e) {
            log.error("[notification_router] Error construyendo Push — orderId={}",
                    envelope.orderId(), e);
            return Optional.empty();
        }
    }
}
