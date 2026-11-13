package com.rappistream.smsworker.listener;

import com.rappistream.common.commands.NotificationCommand;
import com.rappistream.common.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Test unitario para SmsNotificationListener.
 *
 * Verifica que el listener procesa NotificationCommand sin lanzar
 * excepciones (el comportamiento es un log — no hay estado que verificar).
 * El test valida que la integración con el record NotificationCommand
 * funciona correctamente.
 */
class SmsNotificationListenerTest {

    private final SmsNotificationListener listener = new SmsNotificationListener();

    @Test
    @DisplayName("onSmsNotification procesa el comando sin lanzar excepciones")
    void procesaComandoSinExcepcion() {
        // Arrange
        NotificationCommand comando = new NotificationCommand(
                "notif-001",
                "order-123",
                "order-123",
                NotificationType.SMS,
                "Tu repartidor Carlos está llegando. Tiempo estimado: 2 minutos.",
                Instant.now()
        );

        // Act & Assert
        assertDoesNotThrow(() -> listener.onSmsNotification(comando));
    }

    @Test
    @DisplayName("onSmsNotification procesa múltiples comandos consecutivos sin error")
    void procesaMultiplesComandos() {
        for (int i = 0; i < 3; i++) {
            NotificationCommand comando = new NotificationCommand(
                    "notif-00" + i,
                    "order-" + i,
                    "order-" + i,
                    NotificationType.SMS,
                    "Mensaje SMS número " + i,
                    Instant.now()
            );
            assertDoesNotThrow(() -> listener.onSmsNotification(comando));
        }
    }
}
