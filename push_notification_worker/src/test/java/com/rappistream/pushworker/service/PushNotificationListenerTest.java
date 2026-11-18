package com.rappistream.pushworker.service;

import com.rappistream.common.commands.NotificationCommand;
import com.rappistream.common.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PushNotificationListenerTest {

    private final PushNotificationListener listener = new PushNotificationListener();

    @Test @DisplayName("Procesa el comando sin lanzar excepciones")
    void procesaComandoSinExcepcion() {
        assertDoesNotThrow(() -> listener.onPushNotification(new NotificationCommand(
                "notif-push-001", "order-456", "D-001", NotificationType.PUSH,
                "¡Entrega completada! ¡Buen trabajo, Carlos!", Instant.now())));
    }

    @Test @DisplayName("Procesa múltiples comandos consecutivos sin error")
    void procesaMultiplesComandos() {
        for (int i = 0; i < 3; i++) {
            int idx = i;
            assertDoesNotThrow(() -> listener.onPushNotification(new NotificationCommand(
                    "notif-push-00" + idx, "order-" + idx, "D-00" + idx,
                    NotificationType.PUSH, "Push número " + idx, Instant.now())));
        }
    }
}
