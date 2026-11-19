package com.rappistream.orderprocessor.service.strategy;

import com.rappistream.orderprocessor.domain.model.Pedido;

/**
 * Puerto de la estrategia de asignaciÃ³n de repartidores.
 *
 * POO â€” PatrÃ³n Strategy:
 * Define el contrato que deben cumplir todas las estrategias de
 * asignaciÃ³n. El servicio de aplicaciÃ³n depende de esta interfaz,
 * no de ninguna implementaciÃ³n concreta.
 *
 * Principio Abierto/Cerrado (OCP):
 * Para cambiar la polÃ­tica de asignaciÃ³n, se implementa una nueva
 * clase sin modificar el servicio que la usa.
 *
 * SegregaciÃ³n de Interfaces (ISP):
 * Esta interfaz tiene una sola responsabilidad: asignar repartidor.
 * No mezcla responsabilidades de notificaciÃ³n ni persistencia.
 *
 * InversiÃ³n de Dependencias (DIP):
 * Los mÃ³dulos de alto nivel (OrderProcessorService) dependen de
 * esta abstracciÃ³n. Las implementaciones concretas dependen de ella.
 *
 * Implementaciones disponibles:
 * - RoundRobinAssignmentStrategy: asignaciÃ³n circular entre repartidores disponibles
 */
public interface DriverAssignmentStrategy {

    /**
     * Asigna un repartidor disponible al pedido dado.
     *
     * @param pedido el pedido que necesita repartidor
     * @throws IllegalStateException si no hay repartidores disponibles
     */
    void asignar(Pedido pedido);
}

