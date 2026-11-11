package com.rappistream.dashboardbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rappistream.common.dto.OrderStatusDTO;
import com.rappistream.common.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias del InternalUpdateController.
 * Verifica que el controlador delega correctamente al WebSocket.
 * No levanta Spring — usa Mockito puro. Patrón AAA.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InternalUpdateController — WebSocket Broadcast")
class InternalUpdateControllerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private InternalUpdateController controller;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private OrderStatusDTO dtoValido() {
        return new OrderStatusDTO(
                "order-001", "María García", "Hamburguesa", 2,
                "Calle 100", "D-001", "Carlos", OrderStatus.ASIGNADO, Instant.now()
        );
    }

    @Test
    @DisplayName("Debe hacer broadcast al topic /topic/orders")
    void debeHacerBroadcastAlTopicCorrecto() {
        // Arrange
        OrderStatusDTO dto = dtoValido();

        // Act
        controller.recibirActualizacion(dto);

        // Assert
        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/orders"), eq(dto));
    }

    @Test
    @DisplayName("Debe hacer broadcast con el DTO exacto recibido")
    void debeEnviarElDtoExacto() {
        // Arrange
        OrderStatusDTO dto = dtoValido();

        // Act
        controller.recibirActualizacion(dto);

        // Assert — el mensaje enviado por WebSocket debe ser el mismo DTO
        verify(messagingTemplate).convertAndSend("/topic/orders", dto);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Debe retornar 200 OK")
    void debeRetornar200() {
        // Arrange & Act
        var response = controller.recibirActualizacion(dtoValido());

        // Assert
        assertEquals(200, response.getStatusCode().value());
    }

    private void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected: " + expected + " but was: " + actual);
        }
    }
}
