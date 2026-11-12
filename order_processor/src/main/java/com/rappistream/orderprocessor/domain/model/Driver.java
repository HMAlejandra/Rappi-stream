package com.rappistream.orderprocessor.domain.model;

import java.util.Objects;

/**
 * Objeto de dominio que representa un repartidor disponible.
 *
 * POO — Modelo de Dominio Rico:
 * No es una entidad JPA — es un objeto de dominio puro.
 * La capa de infraestructura (JPA) lo mapea desde la tabla 'drivers'
 * y lo convierte a este objeto antes de pasarlo al dominio.
 * El dominio nunca conoce las entidades JPA.
 *
 * Inmutabilidad:
 * Los campos son final. Una vez creado, no cambia.
 */
public class Driver {

    private final String  id;
    private final String  name;
    private final boolean available;

    public Driver(String id, String name, boolean available) {
        Objects.requireNonNull(id,   "id del driver no puede ser nulo");
        Objects.requireNonNull(name, "name del driver no puede ser nulo");

        if (id.isBlank())   throw new IllegalArgumentException("id del driver no puede estar vacío");
        if (name.isBlank()) throw new IllegalArgumentException("name del driver no puede estar vacío");

        this.id        = id;
        this.name      = name;
        this.available = available;
    }

    public String  getId()         { return id; }
    public String  getName()       { return name; }
    public boolean isAvailable()   { return available; }

    @Override
    public String toString() {
        return "Driver{id='" + id + "', name='" + name + "', available=" + available + "}";
    }
}
