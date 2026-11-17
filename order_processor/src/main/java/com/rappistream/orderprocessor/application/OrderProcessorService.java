package com.rappistream.orderprocessor.application;

import com.rappistream.common.commands.OrderCreatedCommand;
import com.rappistream.orderprocessor.domain.model.Pedido;
import com.rappistream.orderprocessor.domain.port.OrderEventPublisherPort;
import com.rappistream.orderprocessor.domain.port.OrderRepositoryPort;
import com.rappistream.orderprocessor.domain.strategy.DriverAssignmentStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Servicio de aplicación — orquesta el procesamiento de un pedido.
 *
 * POO — Separación de capas:
 * Este servicio coordina el dominio (Pedido, Strategy) con la
 * infraestructura (puertos). No contiene lógica de negocio propia —
 * esa vive en Pedido y en los estados concretos (Patrón State).
 *
 * DIP — Inversión de Dependencias:
 * Las tres dependencias se inyectan por constructor como interfaces.
 * Este servicio no conoce KafkaTemplate, JpaRepository ni ningún
 * detalle de infraestructura. Solo habla con puertos abstractos.
 *
 * Principio de Responsabilidad Única (SRP):
 * Solo coordina el flujo de procesamiento de un pedido.
 * No maneja serialización, transporte ni configuración.
 *
 * Flujo de procesamiento:
 * 1. Crear entidad Pedido a partir del comando (Fail-Fast en el constructor)
 * 2. Persistir el pedido inicial en PostgreSQL
 * 3. Publicar ORDER_CREATED en Kafka
 * 4. Aplicar la estrategia de asignación (Strategy → State)
 * 5. Actualizar el estado en PostgreSQL
 * 6. Publicar DRIVER_ASSIGNED en Kafka
 *
 * Nota sobre atomicidad:
 * PostgreSQL y Kafka no comparten una transacción atómica en este MVP.
 * Si Kafka falla después del commit en PG, el evento se pierde.
 * Para producción real se usaría el patrón Outbox.
 * Esto está documentado y es aceptable para el contexto universitario.
 */
@Service
public class OrderProcessorService {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessorService.class);

    // Dependencias inyectadas como interfaces (DIP)
    @Value("${processor.delay.assignment-ms:3000}")
    private long assignmentDelayMs;

    private final OrderRepositoryPort    repositorio;
    private final OrderEventPublisherPort publicador;
    private final DriverAssignmentStrategy estrategiaAsignacion;

    /**
     * Constructor — inyección estricta por constructor.
     * No hay @Autowired en campos. Spring inyecta las implementaciones
     * concretas de los puertos en tiempo de arranque.
     */
    public OrderProcessorService(OrderRepositoryPort repositorio,
                                  OrderEventPublisherPort publicador,
                                  DriverAssignmentStrategy estrategiaAsignacion) {
        this.repositorio          = repositorio;
        this.publicador           = publicador;
        this.estrategiaAsignacion = estrategiaAsignacion;
    }

    /**
     * Procesa el comando de creación de un pedido.
     *
     * @param comando el comando inmutable recibido de RabbitMQ (ya validado por order_api)
     */
    public void procesarComando(OrderCreatedCommand comando) {
        log.info("[order_processor] Procesando comando — orderId={} customer='{}'",
                comando.orderId(), comando.customerName());

        // 1. Crear entidad Pedido (Fail-Fast en el constructor)
        Pedido pedido = new Pedido(
                comando.orderId(),
                comando.customerName(),
                comando.product(),
                comando.quantity(),
                comando.address()
        );
        log.debug("[order_processor] Pedido creado en dominio — estado={}", pedido.getNombreEstado());

        // 2. Persistir estado inicial (CREADO) en PostgreSQL
        repositorio.guardar(pedido);
        log.debug("[order_processor] Pedido persistido en PostgreSQL — orderId={}", pedido.getId());

        // 3. Publicar ORDER_CREATED en Kafka
        publicador.publicarOrderCreated(pedido);
        log.debug("[order_processor] ORDER_CREATED publicado en Kafka — orderId={}", pedido.getId());

        // 3.5 Delay simulado — el dashboard muestra la animación "Buscando repartidor"
        try {
            log.info("[order_processor] Buscando repartidor disponible ({}ms)...", assignmentDelayMs);
            Thread.sleep(assignmentDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 4. Aplicar Strategy de asignación de repartidor
        //    La Strategy llama a pedido.asignarRepartidor(driverId, driverName)
        //    que a su vez delega al EstadoCreado (Patrón State)
        //    que transiciona el pedido a estado ASIGNADO.
        estrategiaAsignacion.asignar(pedido);
        log.info("[order_processor] Repartidor asignado — orderId={} driverId='{}' driverName='{}'",
                pedido.getId(), pedido.getDriverId(), pedido.getDriverName());

        // 5. Actualizar estado en PostgreSQL (ahora ASIGNADO)
        repositorio.actualizar(pedido);

        // 6. Publicar DRIVER_ASSIGNED en Kafka (clave: orderId)
        publicador.publicarDriverAssigned(pedido);
        log.info("[order_processor] DRIVER_ASSIGNED publicado en Kafka — orderId={}", pedido.getId());
    }
}
