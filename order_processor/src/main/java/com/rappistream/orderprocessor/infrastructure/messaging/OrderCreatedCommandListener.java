package com.rappistream.orderprocessor.infrastructure.messaging;

import com.rappistream.common.commands.OrderCreatedCommand;
import com.rappistream.orderprocessor.application.OrderProcessorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listener de RabbitMQ para la cola new_orders_queue.
 *
 * SRP: solo recibe el mensaje y delega al servicio de aplicación.
 * No contiene lógica de negocio.
 *
 * Jackson2JsonMessageConverter (configurado en RabbitMqConfig)
 * deserializa automáticamente el JSON a OrderCreatedCommand
 * antes de que este método sea invocado.
 *
 * Manejo de errores:
 * Si el procesamiento lanza una excepción no controlada,
 * RabbitMQ reencola el mensaje (según la política del broker).
 * Se loguea el error para diagnóstico.
 */
@Component
public class OrderCreatedCommandListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedCommandListener.class);

    private final OrderProcessorService orderProcessorService;

    public OrderCreatedCommandListener(OrderProcessorService orderProcessorService) {
        this.orderProcessorService = orderProcessorService;
    }

    @RabbitListener(queues = "new_orders_queue")
    public void onOrderCreated(OrderCreatedCommand comando) {
        log.info("[order_processor] Comando recibido de RabbitMQ — orderId={} customer='{}'",
                comando.orderId(), comando.customerName());
        try {
            orderProcessorService.procesarComando(comando);
        } catch (Exception e) {
            log.error("[order_processor] Error procesando comando — orderId={}", comando.orderId(), e);
            throw e; // relanzar para que RabbitMQ reencole si está configurado
        }
    }
}
