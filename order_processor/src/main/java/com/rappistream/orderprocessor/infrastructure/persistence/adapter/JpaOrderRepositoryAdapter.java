package com.rappistream.orderprocessor.infrastructure.persistence.adapter;

import com.rappistream.orderprocessor.domain.model.Driver;
import com.rappistream.orderprocessor.domain.model.Pedido;
import com.rappistream.orderprocessor.domain.port.OrderRepositoryPort;
import com.rappistream.orderprocessor.infrastructure.persistence.entity.DriverEntity;
import com.rappistream.orderprocessor.infrastructure.persistence.entity.PedidoEntity;
import com.rappistream.orderprocessor.infrastructure.persistence.repository.SpringDataDriverRepository;
import com.rappistream.orderprocessor.infrastructure.persistence.repository.SpringDataPedidoRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador JPA que implementa OrderRepositoryPort.
 *
 * DIP en acción:
 * El dominio define OrderRepositoryPort (interfaz).
 * Esta clase (infraestructura) la implementa.
 * El dominio nunca importa esta clase.
 *
 * Convierte entre objetos de dominio (Pedido, Driver)
 * y entidades JPA (PedidoEntity, DriverEntity).
 */
@Component
public class JpaOrderRepositoryAdapter implements OrderRepositoryPort {

    private final SpringDataPedidoRepository pedidoRepo;
    private final SpringDataDriverRepository driverRepo;

    public JpaOrderRepositoryAdapter(SpringDataPedidoRepository pedidoRepo,
                                      SpringDataDriverRepository driverRepo) {
        this.pedidoRepo = pedidoRepo;
        this.driverRepo = driverRepo;
    }

    @Override
    public void guardar(Pedido pedido) {
        PedidoEntity entity = toEntity(pedido);
        pedidoRepo.save(entity);
    }

    @Override
    public void actualizar(Pedido pedido) {
        pedidoRepo.findById(pedido.getId()).ifPresent(entity -> {
            entity.setStatus(pedido.getNombreEstado());
            entity.setDriverId(pedido.getDriverId());
            entity.setUpdatedAt(Instant.now());
            pedidoRepo.save(entity);
        });
    }

    @Override
    public Optional<Pedido> buscarPorId(String orderId) {
        return pedidoRepo.findById(orderId).map(this::toDomain);
    }

    @Override
    public List<Driver> obtenerRepartidoresDisponibles() {
        return driverRepo.findByAvailableTrue()
                .stream()
                .map(e -> new Driver(e.getId(), e.getName(), e.isAvailable()))
                .toList();
    }

    // ------------------------------------------------
    // Conversiones dominio ↔ entidad JPA
    // ------------------------------------------------

    private PedidoEntity toEntity(Pedido pedido) {
        return new PedidoEntity(
                pedido.getId(),
                pedido.getCustomerName(),
                pedido.getProduct(),
                pedido.getQuantity(),
                pedido.getAddress(),
                pedido.getDriverId(),
                pedido.getNombreEstado(),
                pedido.getCreadoEn(),
                pedido.getActualizadoEn()
        );
    }

    private Pedido toDomain(PedidoEntity entity) {
        // Reconstruye el pedido desde BD (solo para consultas — no usado en el flujo principal)
        Pedido pedido = new Pedido(
                entity.getId(),
                entity.getCustomerName(),
                entity.getProduct(),
                entity.getQuantity(),
                entity.getAddress()
        );
        return pedido;
    }
}
