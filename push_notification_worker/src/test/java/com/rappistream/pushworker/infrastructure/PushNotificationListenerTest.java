package com.rappistream.pushworker.infrastructure;

import com.rappistream.common.commands.NotificationCommand;
import com.rappistream.common.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PushNotificationListenerTest {

    private final PushNotificationListener listener = new PushNotificationListener();

    @Test
    @DisplayName("onPushNotification procesa el comando sin lanzar excepciones")
    void procesaComandoSinExcepcion() {
        NotificationCommand comando = new NotificationCommand(
                "notif-push-001", "order-456", "D-001",
                NotificationType.PUSH,
                "¡Entrega completada! Pedido order-456 entregado exitosamente. ¡Buen trabajo, Carlos!",
                Instant.now()
        );
        assertDoesNotThrow(() -> listener.onPushNotification(comando));
    }

    @Test
    @DisplayName("onPushNotification procesa múltiples comandos consecutivos sin error")
    void procesaMultiplesComandos() {
        for (int i = 0; i < 3; i++) {
            NotificationCommand comando = new NotificationCommand(
                    "notif-push-00" + i, "order-" + i, "D-00" + i,
                    NotificationType.PUSH, "Push número " + i, Instant.now()
            );
            assertDoesNotThrow(() -> listener.onPushNotification(comando));
        }
    }
}
