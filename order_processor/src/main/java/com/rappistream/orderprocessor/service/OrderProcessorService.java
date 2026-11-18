package com.rappistream.orderprocessor.service;

import com.rappistream.common.commands.OrderCreatedCommand;
import com.rappistream.orderprocessor.domain.DriverAssignmentStrategy;
import com.rappistream.orderprocessor.domain.Pedido;
import com.rappistream.orderprocessor.repository.OrderRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Servicio de aplicación — orquesta el procesamiento de un pedido.
 *
 * Los servicios orquestan, el dominio decide.
 * Depende solo de interfaces (DIP): no conoce JPA ni Kafka directamente.
 *
 * Flujo: guardar → publicarOrderCreated → asignar repartidor
 *        → actualizar → publicarDriverAssigned
 */
@Service
public class OrderProcessorService {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessorService.class);

    @Value("${processor.delay.assignment-ms:3000}")
    private long assignmentDelayMs;

    private final OrderRepositoryPort      repositorio;
    private final OrderEventPublisherPort  publicador;
    private final DriverAssignmentStrategy estrategiaAsignacion;

    public OrderProcessorService(OrderRepositoryPort repositorio,
                                  OrderEventPublisherPort publicador,
                                  DriverAssignmentStrategy estrategiaAsignacion) {
        this.repositorio          = repositorio;
        this.publicador           = publicador;
        this.estrategiaAsignacion = estrategiaAsignacion;
    }

    public void procesarComando(OrderCreatedCommand comando) {
        log.info("[order_processor] Procesando comando — orderId={} customer='{}'",
                comando.orderId(), comando.customerName());

        Pedido pedido = new Pedido(comando.orderId(), comando.customerName(),
                comando.product(), comando.quantity(), comando.address());

        repositorio.guardar(pedido);
        publicador.publicarOrderCreated(pedido);

        try {
            log.info("[order_processor] Buscando repartidor disponible ({}ms)...", assignmentDelayMs);
            Thread.sleep(assignmentDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        estrategiaAsignacion.asignar(pedido);
        log.info("[order_processor] Repartidor asignado — orderId={} driverId='{}' driverName='{}'",
                pedido.getId(), pedido.getDriverId(), pedido.getDriverName());

        repositorio.actualizar(pedido);
        publicador.publicarDriverAssigned(pedido);
        log.info("[order_processor] DRIVER_ASSIGNED publicado — orderId={}", pedido.getId());
    }
}
