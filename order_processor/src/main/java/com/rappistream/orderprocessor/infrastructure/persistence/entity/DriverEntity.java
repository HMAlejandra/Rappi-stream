package com.rappistream.orderprocessor.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entidad JPA que mapea la tabla 'drivers' de PostgreSQL.
 * Los repartidores semilla (D-001..D-005) fueron insertados
 * por infrastructure/postgres/init.sql.
 */
@Entity
@Table(name = "drivers")
public class DriverEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "available", nullable = false)
    private boolean available;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DriverEntity() {}

    public String getId()        { return id; }
    public String getName()      { return name; }
    public boolean isAvailable() { return available; }
    public Instant getCreatedAt(){ return createdAt; }
}
