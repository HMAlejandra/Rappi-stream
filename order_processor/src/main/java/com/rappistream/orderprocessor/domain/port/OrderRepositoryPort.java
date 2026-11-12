package com.rappistream.orderprocessor.domain.port;

import com.rappistream.orderprocessor.domain.model.Driver;
import com.rappistream.orderprocessor.domain.model.Pedido;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para la persistencia de pedidos y repartidores.
 *
 * DIP — Inversión de Dependencias:
 * El dominio define este contrato. La capa de infraestructura
 * (JpaOrderRepository) lo implementa usando Spring Data JPA.
 *
 * El dominio nunca importa clases de JPA, Hibernate ni Spring Data.
 * Toda la dependencia fluye en una sola dirección:
 * infraestructura → dominio (nunca al revés).
 *
 * Segregación de Interfaces (ISP):
 * Solo declara las operaciones que el dominio necesita.
 * No expone métodos de infraestructura como flush(), merge(), etc.
 */
public interface OrderRepositoryPort {

    /**
     * Persiste un nuevo pedido en la base de datos.
     *
     * @param pedido el pedido a guardar
     */
    void guardar(Pedido pedido);

    /**
     * Actualiza el estado de un pedido existente.
     *
     * @param pedido el pedido con el nuevo estado
     */
    void actualizar(Pedido pedido);

    /**
     * Busca un pedido por su identificador.
     *
     * @param orderId UUID del pedido
     * @return Optional vacío si no existe
     */
    Optional<Pedido> buscarPorId(String orderId);

    /**
     * Retorna todos los repartidores disponibles.
     * Usado por la Strategy para construir la lista de candidatos.
     *
     * @return lista de repartidores disponibles (puede estar vacía)
     */
    List<Driver> obtenerRepartidoresDisponibles();
}
