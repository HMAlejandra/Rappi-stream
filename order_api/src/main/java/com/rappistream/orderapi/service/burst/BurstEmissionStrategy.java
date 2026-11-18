package com.rappistream.orderapi.service.burst;

/**
 * Estrategia de emisión de una ráfaga (patrón Strategy).
 *
 * Permite variar la política de publicación (ritmo constante, escalonada,
 * etc.) sin tocar la orquestación ni el controlador. Cada implementación
 * debe acotar la concurrencia y cerrar el pool de hilos al terminar.
 */
public interface BurstEmissionStrategy {

    /**
     * Ejecuta la emisión de la ráfaga usando el puerto de publicación.
     */
    void ejecutar(BurstOrden orden, BurstOrderPublisherPort publisher);
}
