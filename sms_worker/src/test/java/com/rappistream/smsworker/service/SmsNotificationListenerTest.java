package com.rappistream.smsworker.service;

import com.rappistream.common.commands.NotificationCommand;
import com.rappistream.common.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SmsNotificationListenerTest {

    private final SmsNotificationListener listener = new SmsNotificationListener();

    @Test @DisplayName("Procesa el comando sin lanzar excepciones")
    void procesaComandoSinExcepcion() {
        assertDoesNotThrow(() -> listener.onSmsNotification(new NotificationCommand(
                "notif-001", "order-123", "order-123", NotificationType.SMS,
                "Tu repartidor Carlos está llegando.", Instant.now())));
    }

    @Test @DisplayName("Procesa múltiples comandos consecutivos sin error")
    void procesaMultiplesComandos() {
        for (int i = 0; i < 3; i++) {
            int idx = i;
            assertDoesNotThrow(() -> listener.onSmsNotification(new NotificationCommand(
                    "notif-00" + idx, "order-" + idx, "order-" + idx,
                    NotificationType.SMS, "Mensaje " + idx, Instant.now())));
        }
    }
}
