package com.rappistream.orderprocessor.domain;

import java.util.Objects;

/**
 * Objeto de dominio que representa un repartidor disponible.
 *
 * Modelo rico — inmutable por diseño.
 * No es una entidad JPA. El adaptador JPA lo convierte desde
 * DriverEntity antes de pasarlo al dominio.
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

    public String  getId()       { return id; }
    public String  getName()     { return name; }
    public boolean isAvailable() { return available; }

    @Override
    public String toString() {
        return "Driver{id='" + id + "', name='" + name + "', available=" + available + "}";
    }
}
