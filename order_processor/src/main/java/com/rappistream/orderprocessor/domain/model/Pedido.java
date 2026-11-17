package com.rappistream.orderprocessor.domain.model;

import com.rappistream.orderprocessor.domain.state.EstadoCreado;
import com.rappistream.orderprocessor.domain.state.OrderState;

import java.time.Instant;
import java.util.Objects;

/**
 * Entidad rica del dominio — Pedido.
 * Encapsula estado y comportamiento. Patrón State para transiciones.
 * No tiene setters indiscriminados — el estado cambia solo por métodos semánticos.
 * No importa JPA ni Spring — es un POJO puro.
 */
public class Pedido {

    private final String  id;
    private final String  customerName;
    private final String  product;
    private final int     quantity;
    private final String  address;
    private final Instant creadoEn;

    private String     driverId;
    private String     driverName;
    private Instant    actualizadoEn;
    OrderState estadoActual; // package-private para los estados concretos

    public Pedido(String id, String customerName, String product,
                  int quantity, String address) {
        Objects.requireNonNull(id,           "id no puede ser nulo");
        Objects.requireNonNull(customerName, "customerName no puede ser nulo");
        Objects.requireNonNull(product,      "product no puede ser nulo");
        Objects.requireNonNull(address,      "address no puede ser nulo");
        if (id.isBlank())          throw new IllegalArgumentException("id no puede estar vacío");
        if (customerName.isBlank()) throw new IllegalArgumentException("customerName no puede estar vacío");
        if (product.isBlank())     throw new IllegalArgumentException("product no puede estar vacío");
        if (quantity <= 0)         throw new IllegalArgumentException("quantity debe ser mayor que cero, recibido: " + quantity);
        if (address.isBlank())     throw new IllegalArgumentException("address no puede estar vacía");

        this.id            = id;
        this.customerName  = customerName;
        this.product       = product;
        this.quantity      = quantity;
        this.address       = address;
        this.creadoEn      = Instant.now();
        this.actualizadoEn = this.creadoEn;
        this.estadoActual  = new EstadoCreado();
    }

    // ── Métodos de negocio (Patrón State) ──────────────────────────────

    public void asignarRepartidor(String driverId, String driverName) {
        estadoActual.asignarRepartidor(this, driverId, driverName);
    }

    public void iniciarEntrega() {
        estadoActual.iniciarEntrega(this);
        this.actualizadoEn = Instant.now();
    }

    public void registrarLlegada() {
        estadoActual.registrarLlegada(this);
        this.actualizadoEn = Instant.now();
    }

    public void completarEntrega() {
        estadoActual.completarEntrega(this);
        this.actualizadoEn = Instant.now();
    }

    /** Usado por los estados concretos para aplicar la asignación. */
    public void aplicarAsignacion(String driverId, String driverName) {
        Objects.requireNonNull(driverId,   "driverId no puede ser nulo en la asignación");
        Objects.requireNonNull(driverName, "driverName no puede ser nulo en la asignación");
        if (driverId.isBlank())   throw new IllegalArgumentException("driverId no puede estar vacío");
        if (driverName.isBlank()) throw new IllegalArgumentException("driverName no puede estar vacío");
        this.driverId      = driverId;
        this.driverName    = driverName;
        this.actualizadoEn = Instant.now();
    }

    public void setEstado(OrderState nuevoEstado) {
        this.estadoActual = nuevoEstado;
    }

    // ── Getters ────────────────────────────────────────────────────────

    public String  getId()             { return id; }
    public String  getCustomerName()   { return customerName; }
    public String  getProduct()        { return product; }
    public int     getQuantity()       { return quantity; }
    public String  getAddress()        { return address; }
    public String  getDriverId()       { return driverId; }
    public String  getDriverName()     { return driverName; }
    public Instant getCreadoEn()       { return creadoEn; }
    public Instant getActualizadoEn()  { return actualizadoEn; }
    public String  getNombreEstado()   { return estadoActual.nombre(); }

    @Override
    public String toString() {
        return "Pedido{id='" + id + "', customer='" + customerName +
               "', estado='" + getNombreEstado() + "', driver='" + driverId + "'}";
    }
}
