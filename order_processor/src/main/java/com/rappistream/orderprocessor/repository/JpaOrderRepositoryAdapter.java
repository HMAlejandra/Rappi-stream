package com.rappistream.orderprocessor.repository;

import com.rappistream.orderprocessor.domain.Driver;
import com.rappistream.orderprocessor.domain.Pedido;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador JPA — implementa OrderRepositoryPort.
 * Convierte entre Pedido (dominio) ↔ PedidoEntity (JPA).
 * El dominio nunca ve esta clase.
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
        pedidoRepo.save(toEntity(pedido));
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

    private PedidoEntity toEntity(Pedido p) {
        return new PedidoEntity(p.getId(), p.getCustomerName(), p.getProduct(),
                p.getQuantity(), p.getAddress(), p.getDriverId(),
                p.getNombreEstado(), p.getCreadoEn(), p.getActualizadoEn());
    }

    private Pedido toDomain(PedidoEntity e) {
        return new Pedido(e.getId(), e.getCustomerName(), e.getProduct(),
                e.getQuantity(), e.getAddress());
    }
}
