package com.rappistream.driversimulator.exception;

/**
 * Excepción base para errores del simulador de repartidores.
 *
 * Jerarquía:
 *   RuntimeException
 *   └── DriverSimulatorException ← abstract base
 *       └── DeliverySimulationException ← fallo en la simulación
 */
public abstract class DriverSimulatorException extends RuntimeException {

    protected DriverSimulatorException(String mensaje) {
        super(mensaje);
    }
}
