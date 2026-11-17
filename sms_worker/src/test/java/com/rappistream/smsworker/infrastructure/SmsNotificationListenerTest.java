package com.rappistream.smsworker.infrastructure;

import com.rappistream.common.commands.NotificationCommand;
import com.rappistream.common.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SmsNotificationListenerTest {

    private final SmsNotificationListener listener = new SmsNotificationListener();

    @Test
    @DisplayName("onSmsNotification procesa el comando sin lanzar excepciones")
    void procesaComandoSinExcepcion() {
        NotificationCommand comando = new NotificationCommand(
                "notif-001", "order-123", "order-123",
                NotificationType.SMS,
                "Tu repartidor Carlos está llegando. Tiempo estimado: 2 minutos.",
                Instant.now()
        );
        assertDoesNotThrow(() -> listener.onSmsNotification(comando));
    }

    @Test
    @DisplayName("onSmsNotification procesa múltiples comandos consecutivos sin error")
    void procesaMultiplesComandos() {
        for (int i = 0; i < 3; i++) {
            NotificationCommand comando = new NotificationCommand(
                    "notif-00" + i, "order-" + i, "order-" + i,
                    NotificationType.SMS, "Mensaje SMS número " + i, Instant.now()
            );
            assertDoesNotThrow(() -> listener.onSmsNotification(comando));
        }
    }
}
