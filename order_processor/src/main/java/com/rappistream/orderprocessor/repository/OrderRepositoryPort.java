package com.rappistream.orderprocessor.repository;

import com.rappistream.orderprocessor.domain.Driver;
import com.rappistream.orderprocessor.domain.Pedido;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para la persistencia de pedidos y repartidores.
 *
 * DIP: el dominio define este contrato. La infraestructura lo implementa.
 * El dominio nunca importa JPA, Hibernate ni Spring Data.
 */
public interface OrderRepositoryPort {

    void guardar(Pedido pedido);
    void actualizar(Pedido pedido);
    Optional<Pedido> buscarPorId(String orderId);
    List<Driver> obtenerRepartidoresDisponibles();
}
