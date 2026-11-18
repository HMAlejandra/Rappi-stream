package com.rappistream.orderapi.service.burst;

import com.rappistream.orderapi.dto.request.CreateOrderRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Pruebas de la estrategia FixedRateBurstStrategy con un puerto mock (AAA).
 */
class FixedRateBurstStrategyTest {

    @Test
    void publicaExactamenteTodosLosPedidosDelObjetivo() {
        // Arrange
        BurstOrden orden = new BurstOrden("b1", 20, 4, 0, "C", "P", "A");
        BurstOrderPublisherPort publisher = mock(BurstOrderPublisherPort.class);
        when(publisher.publicarComando(any())).thenReturn("order-id");
        FixedRateBurstStrategy strategy = new FixedRateBurstStrategy();

        // Act
        strategy.ejecutar(orden, publisher);

        // Assert
        verify(publisher, times(20)).publicarComando(any(CreateOrderRequest.class));
        assertTrue(orden.estaCompleta());
    }

    @Test
    void dejaLaOrdenCompletaTrasEmitir() {
        // Arrange
        BurstOrden orden = new BurstOrden("b2", 5, 2, 0, "C", "P", "A");
        BurstOrderPublisherPort publisher = mock(BurstOrderPublisherPort.class);
        when(publisher.publicarComando(any())).thenReturn("order-id");
        FixedRateBurstStrategy strategy = new FixedRateBurstStrategy();

        // Act
        strategy.ejecutar(orden, publisher);

        // Assert
        assertEquals5Publicaciones(publisher);
        assertTrue(orden.estaCompleta());
    }

    private void assertEquals5Publicaciones(BurstOrderPublisherPort publisher) {
        verify(publisher, times(5)).publicarComando(any(CreateOrderRequest.class));
    }
}
