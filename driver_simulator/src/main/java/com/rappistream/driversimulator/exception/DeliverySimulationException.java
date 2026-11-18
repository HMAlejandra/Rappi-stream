package com.rappistream.driversimulator.exception;

/**
 * Se lanza cuando falla la simulación del ciclo de entrega
 * (error de serialización, Kafka no disponible, etc.).
 */
public class DeliverySimulationException extends DriverSimulatorException {

    public DeliverySimulationException(String orderId, Throwable causa) {
        super("Error simulando entrega del pedido '" + orderId + "': " + causa.getMessage());
    }
}
