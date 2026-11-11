package com.rappistream.orderprocessor.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entidad JPA que mapea la tabla 'orders' de PostgreSQL.
 *
 * DIP — Separación de capas:
 * Esta clase es de infraestructura pura. El dominio (Pedido.java)
 * no conoce su existencia. El adaptador JpaOrderRepositoryAdapter
 * convierte entre Pedido (dominio) y PedidoEntity (JPA).
 *
 * La tabla 'orders' fue creada por infrastructure/postgres/init.sql.
 */
@Entity
@Table(name = "orders")
public class PedidoEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "customer_name", length = 150, nullable = false)
    private String customerName;

    @Column(name = "product", length = 200, nullable = false)
    private String product;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "driver_id", length = 36)
    private String driverId;

    @Column(name = "status", length = 30, nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // JPA requiere constructor sin argumentos
    protected PedidoEntity() {}

    public PedidoEntity(String id, String customerName, String product,
                        int quantity, String address, String driverId,
                        String status, Instant createdAt, Instant updatedAt) {
        this.id           = id;
        this.customerName = customerName;
        this.product      = product;
        this.quantity     = quantity;
        this.address      = address;
        this.driverId     = driverId;
        this.status       = status;
        this.createdAt    = createdAt;
        this.updatedAt    = updatedAt;
    }

    public String getId()           { return id; }
    public String getCustomerName() { return customerName; }
    public String getProduct()      { return product; }
    public int    getQuantity()     { return quantity; }
    public String getAddress()      { return address; }
    public String getDriverId()     { return driverId; }
    public String getStatus()       { return status; }
    public Instant getCreatedAt()   { return createdAt; }
    public Instant getUpdatedAt()   { return updatedAt; }

    public void setStatus(String status)       { this.status = status; }
    public void setDriverId(String driverId)   { this.driverId = driverId; }
    public void setUpdatedAt(Instant updatedAt){ this.updatedAt = updatedAt; }
}
