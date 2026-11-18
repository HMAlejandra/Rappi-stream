package com.rappistream.orderprocessor.service;

import com.rappistream.common.commands.OrderCreatedCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listener de RabbitMQ para la cola new_orders_queue.
 *
 * SRP: recibe el mensaje y delega al servicio.
 * No contiene lógica de negocio.
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
        log.info("[order_processor] Comando recibido — orderId={} customer='{}'",
                comando.orderId(), comando.customerName());
        try {
            orderProcessorService.procesarComando(comando);
        } catch (Exception e) {
            log.error("[order_processor] Error procesando comando — orderId={}", comando.orderId(), e);
            throw e;
        }
    }
}
