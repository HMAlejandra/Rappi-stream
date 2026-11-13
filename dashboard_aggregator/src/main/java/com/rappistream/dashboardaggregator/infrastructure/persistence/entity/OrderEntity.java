package com.rappistream.dashboardaggregator.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entidad JPA — tabla 'orders'.
 *
 * El aggregator la usa para actualizar el estado consolidado
 * cada vez que procesa un evento de Kafka.
 *
 * DIP: esta clase es de infraestructura. El dominio en memoria
 * usa OrderSnapshot (POJO puro) — no esta entidad.
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "customer_name", length = 150)
    private String customerName;

    @Column(name = "product", length = 200)
    private String product;

    @Column(name = "quantity")
    private int quantity;

    @Column(name = "address")
    private String address;

    @Column(name = "driver_id", length = 36)
    private String driverId;

    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected OrderEntity() {}

    public OrderEntity(String id, String customerName, String product,
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

    public String  getId()           { return id; }
    public String  getCustomerName() { return customerName; }
    public String  getProduct()      { return product; }
    public int     getQuantity()     { return quantity; }
    public String  getAddress()      { return address; }
    public String  getDriverId()     { return driverId; }
    public String  getStatus()       { return status; }
    public Instant getCreatedAt()    { return createdAt; }
    public Instant getUpdatedAt()    { return updatedAt; }

    public void setStatus(String status)        { this.status    = status; }
    public void setDriverId(String driverId)    { this.driverId  = driverId; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
