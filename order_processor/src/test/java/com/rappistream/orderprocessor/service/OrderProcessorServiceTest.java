package com.rappistream.orderprocessor.service;

import com.rappistream.common.commands.OrderCreatedCommand;
import com.rappistream.orderprocessor.domain.DriverAssignmentStrategy;
import com.rappistream.orderprocessor.domain.Pedido;
import com.rappistream.orderprocessor.repository.OrderRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderProcessorService — Orquestación")
class OrderProcessorServiceTest {

    @Mock private OrderRepositoryPort     repositorio;
    @Mock private OrderEventPublisherPort publicador;
    @Mock private DriverAssignmentStrategy estrategiaAsignacion;

    @InjectMocks
    private OrderProcessorService service;

    private OrderCreatedCommand comandoValido() {
        return new OrderCreatedCommand(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "María García", "Hamburguesa doble", 2, "Calle 100 #15-30", Instant.now());
    }

    @Test @DisplayName("Debe persistir el pedido antes de publicar eventos")
    void debePersistirAntesDePubilcar() {
        service.procesarComando(comandoValido());
        verify(repositorio, times(1)).guardar(any(Pedido.class));
    }

    @Test @DisplayName("Debe publicar ORDER_CREATED en Kafka")
    void debePublicarOrderCreated() {
        service.procesarComando(comandoValido());
        verify(publicador, times(1)).publicarOrderCreated(any(Pedido.class));
    }

    @Test @DisplayName("Debe aplicar la estrategia de asignación")
    void debeAplicarEstrategiaDeAsignacion() {
        service.procesarComando(comandoValido());
        verify(estrategiaAsignacion, times(1)).asignar(any(Pedido.class));
    }

    @Test @DisplayName("Debe publicar DRIVER_ASSIGNED en Kafka")
    void debePublicarDriverAssigned() {
        service.procesarComando(comandoValido());
        verify(publicador, times(1)).publicarDriverAssigned(any(Pedido.class));
    }

    @Test @DisplayName("Debe actualizar el pedido en la BD después de asignar repartidor")
    void debeActualizarDespuesDeAsignar() {
        service.procesarComando(comandoValido());
        verify(repositorio, times(1)).actualizar(any(Pedido.class));
    }

    @Test @DisplayName("El pedido enviado a guardar debe usar el orderId del comando")
    void pedidoGuardadoDebeUsarElOrderIdDelComando() {
        OrderCreatedCommand comando = comandoValido();
        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        service.procesarComando(comando);
        verify(repositorio).guardar(captor.capture());
        Pedido pedidoGuardado = captor.getValue();
        assertEquals(comando.orderId(),      pedidoGuardado.getId());
        assertEquals(comando.customerName(), pedidoGuardado.getCustomerName());
        assertEquals(comando.product(),      pedidoGuardado.getProduct());
        assertEquals(comando.quantity(),     pedidoGuardado.getQuantity());
    }

    @Test @DisplayName("Debe ejecutar las operaciones en el orden correcto")
    void debeEjecutarEnOrdenCorrecto() {
        service.procesarComando(comandoValido());
        var orden = inOrder(repositorio, publicador, estrategiaAsignacion);
        orden.verify(repositorio).guardar(any());
        orden.verify(publicador).publicarOrderCreated(any());
        orden.verify(estrategiaAsignacion).asignar(any());
        orden.verify(repositorio).actualizar(any());
        orden.verify(publicador).publicarDriverAssigned(any());
    }

    @Test @DisplayName("Si la estrategia falla, no debe publicar DRIVER_ASSIGNED")
    void siEstrategiaFallaNODebePublicarDriverAssigned() {
        doThrow(new IllegalStateException("Sin repartidores"))
                .when(estrategiaAsignacion).asignar(any());
        assertThrows(IllegalStateException.class, () -> service.procesarComando(comandoValido()));
        verify(publicador, never()).publicarDriverAssigned(any());
    }
}
