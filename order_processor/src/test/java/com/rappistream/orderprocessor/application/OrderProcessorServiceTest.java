package com.rappistream.orderprocessor.application;

import com.rappistream.common.commands.OrderCreatedCommand;
import com.rappistream.orderprocessor.domain.model.Pedido;
import com.rappistream.orderprocessor.domain.port.OrderEventPublisherPort;
import com.rappistream.orderprocessor.domain.port.OrderRepositoryPort;
import com.rappistream.orderprocessor.domain.strategy.DriverAssignmentStrategy;
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

/**
 * Pruebas unitarias del servicio de orquestación — OrderProcessorService.
 *
 * Estrategia:
 * Se mockean los tres puertos (repositorio, publicador, estrategia).
 * No se levanta Spring Boot, Kafka ni PostgreSQL.
 * Se verifica que el servicio orqueste correctamente llamando
 * a cada puerto en el orden correcto.
 *
 * POO — Mockito:
 * @Mock crea dobles de prueba de las interfaces de puerto.
 * @InjectMocks inyecta los mocks en el constructor de OrderProcessorService.
 * verify() confirma que el servicio llamó a los puertos correctamente.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderProcessorService — Orquestación con Mockito")
class OrderProcessorServiceTest {

    @Mock
    private OrderRepositoryPort repositorio;

    @Mock
    private OrderEventPublisherPort publicador;

    @Mock
    private DriverAssignmentStrategy estrategiaAsignacion;

    @InjectMocks
    private OrderProcessorService service;

    private OrderCreatedCommand comandoValido() {
        return new OrderCreatedCommand(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "María García",
                "Hamburguesa doble",
                2,
                "Calle 100 #15-30",
                Instant.now()
        );
    }

    @Test
    @DisplayName("Debe persistir el pedido antes de publicar eventos")
    void debePersistirAntesDePubilcar() {
        // Arrange
        OrderCreatedCommand comando = comandoValido();

        // Act
        service.procesarComando(comando);

        // Assert: guardar fue llamado
        verify(repositorio, times(1)).guardar(any(Pedido.class));
    }

    @Test
    @DisplayName("Debe publicar ORDER_CREATED en Kafka")
    void debePublicarOrderCreated() {
        // Arrange
        OrderCreatedCommand comando = comandoValido();

        // Act
        service.procesarComando(comando);

        // Assert
        verify(publicador, times(1)).publicarOrderCreated(any(Pedido.class));
    }

    @Test
    @DisplayName("Debe aplicar la estrategia de asignación")
    void debeAplicarEstrategiaDeAsignacion() {
        // Arrange
        OrderCreatedCommand comando = comandoValido();

        // Act
        service.procesarComando(comando);

        // Assert: la estrategia fue invocada con el pedido
        verify(estrategiaAsignacion, times(1)).asignar(any(Pedido.class));
    }

    @Test
    @DisplayName("Debe publicar DRIVER_ASSIGNED en Kafka")
    void debePublicarDriverAssigned() {
        // Arrange
        OrderCreatedCommand comando = comandoValido();

        // Act
        service.procesarComando(comando);

        // Assert
        verify(publicador, times(1)).publicarDriverAssigned(any(Pedido.class));
    }

    @Test
    @DisplayName("Debe actualizar el pedido en la BD después de asignar repartidor")
    void debeActualizarDespuesDeAsignar() {
        // Arrange
        OrderCreatedCommand comando = comandoValido();

        // Act
        service.procesarComando(comando);

        // Assert: actualizar fue llamado después de guardar
        verify(repositorio, times(1)).actualizar(any(Pedido.class));
    }

    @Test
    @DisplayName("El pedido enviado a guardar debe tener el orderId del comando")
    void pedidoGuardadoDebeUsarElOrderIdDelComando() {
        // Arrange
        OrderCreatedCommand comando = comandoValido();
        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);

        // Act
        service.procesarComando(comando);

        // Assert
        verify(repositorio).guardar(captor.capture());
        Pedido pedidoGuardado = captor.getValue();
        assertEquals(comando.orderId(),      pedidoGuardado.getId());
        assertEquals(comando.customerName(), pedidoGuardado.getCustomerName());
        assertEquals(comando.product(),      pedidoGuardado.getProduct());
        assertEquals(comando.quantity(),     pedidoGuardado.getQuantity());
    }

    @Test
    @DisplayName("Debe ejecutar las operaciones en el orden correcto")
    void debeEjecutarEnOrdenCorrecto() {
        // Arrange
        OrderCreatedCommand comando = comandoValido();

        // Act
        service.procesarComando(comando);

        // Assert: orden: guardar → publicarCreated → asignar → actualizar → publicarAssigned
        var ordenVerificacion = inOrder(repositorio, publicador, estrategiaAsignacion);
        ordenVerificacion.verify(repositorio).guardar(any());
        ordenVerificacion.verify(publicador).publicarOrderCreated(any());
        ordenVerificacion.verify(estrategiaAsignacion).asignar(any());
        ordenVerificacion.verify(repositorio).actualizar(any());
        ordenVerificacion.verify(publicador).publicarDriverAssigned(any());
    }

    @Test
    @DisplayName("Si la estrategia lanza excepción, no debe publicar DRIVER_ASSIGNED")
    void siEstrategiaFallaNODebePublicarDriverAssigned() {
        // Arrange
        OrderCreatedCommand comando = comandoValido();
        doThrow(new IllegalStateException("Sin repartidores"))
                .when(estrategiaAsignacion).asignar(any());

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> service.procesarComando(comando));

        // No se publicó DRIVER_ASSIGNED porque la asignación falló
        verify(publicador, never()).publicarDriverAssigned(any());
    }
}
