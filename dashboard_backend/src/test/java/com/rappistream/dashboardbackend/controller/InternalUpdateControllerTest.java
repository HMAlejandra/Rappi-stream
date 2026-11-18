package com.rappistream.dashboardbackend.controller;

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

@ExtendWith(MockitoExtension.class)
@DisplayName("InternalUpdateController — WebSocket Broadcast")
class InternalUpdateControllerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @InjectMocks private InternalUpdateController controller;

    private OrderStatusDTO dtoValido() {
        return new OrderStatusDTO("order-001", "María García", "Hamburguesa", 2,
                "Calle 100", "D-001", "Carlos", OrderStatus.ASIGNADO, Instant.now());
    }

    @Test @DisplayName("Debe hacer broadcast al topic /topic/orders")
    void debeHacerBroadcastAlTopicCorrecto() {
        OrderStatusDTO dto = dtoValido();
        controller.recibirActualizacion(dto);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/orders"), eq(dto));
    }

    @Test @DisplayName("Debe enviar el DTO exacto recibido")
    void debeEnviarElDtoExacto() {
        OrderStatusDTO dto = dtoValido();
        controller.recibirActualizacion(dto);
        verify(messagingTemplate).convertAndSend("/topic/orders", dto);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test @DisplayName("Debe retornar 200 OK")
    void debeRetornar200() {
        var response = controller.recibirActualizacion(dtoValido());
        assert response.getStatusCode().value() == 200;
    }
}
