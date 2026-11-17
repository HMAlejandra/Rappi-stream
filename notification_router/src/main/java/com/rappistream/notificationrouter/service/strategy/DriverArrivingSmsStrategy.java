package com.rappistream.notificationrouter.service.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappistream.common.commands.NotificationCommand;
import com.rappistream.common.enums.NotificationType;
import com.rappistream.common.enums.OrderEventType;
import com.rappistream.common.events.OrderEventEnvelope;
import com.rappistream.common.events.payloads.DriverArrivingPayload;
import com.rappistream.notificationrouter.service.NotificationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Estrategia concreta: DRIVER_ARRIVING â†’ SMS al cliente.
 *
 * POO â€” PatrÃ³n Strategy (implementaciÃ³n concreta):
 * Cuando el repartidor estÃ¡ llegando, construye un NotificationCommand
 * de tipo SMS dirigido al cliente del pedido.
 *
 * Routing: notify.client.sms â†’ client_sms_queue â†’ sms_worker
 */
@Component
public class DriverArrivingSmsStrategy implements NotificationStrategy {

    private static final Logger log = LoggerFactory.getLogger(DriverArrivingSmsStrategy.class);

    private final ObjectMapper objectMapper;

    public DriverArrivingSmsStrategy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean aplica(OrderEventType eventType) {
        return eventType == OrderEventType.DRIVER_ARRIVING;
    }

    @Override
    public Optional<NotificationCommand> construir(OrderEventEnvelope envelope) {
        try {
            DriverArrivingPayload payload =
                    objectMapper.readValue(envelope.payload(), DriverArrivingPayload.class);

            String mensaje = String.format(
                    "Tu repartidor %s estÃ¡ llegando. Tiempo estimado: %s.",
                    payload.driverName(), payload.estimatedArrival()
            );

            NotificationCommand comando = new NotificationCommand(
                    UUID.randomUUID().toString(),
                    envelope.orderId(),
                    envelope.orderId(),         // recipientId = orderId (cliente del pedido)
                    NotificationType.SMS,
                    mensaje,
                    Instant.now()
            );

            log.info("[notification_router] SMS preparado â€” orderId={} driver={}",
                    envelope.orderId(), payload.driverName());
            return Optional.of(comando);

        } catch (Exception e) {
            log.error("[notification_router] Error construyendo SMS â€” orderId={}",
                    envelope.orderId(), e);
            return Optional.empty();
        }
    }
}

