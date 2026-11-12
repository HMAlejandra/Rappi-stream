package com.rappistream.orderprocessor.domain.model;

import com.rappistream.orderprocessor.domain.state.EstadoCreado;
import com.rappistream.orderprocessor.domain.state.OrderState;

import java.time.Instant;
import java.util.Objects;

/**
 * Entidad rica del dominio — Pedido.
 *
 * POO — Modelo de Dominio Rico vs. Anémico:
 * Esta clase encapsula tanto el estado como el comportamiento del pedido.
 * No expone setters indiscriminados. Las mutaciones de estado se realizan
 * exclusivamente a través de métodos de negocio que validan invariantes.
 *
 * POO — Patrón State:
 * El estado actual se delega a un objeto OrderState. Cada llamada a un
 * método de negocio (asignarRepartidor, iniciarEntrega, etc.) es redirigida
 * al estado actual, que decide si la transición es válida.
 * Esto elimina completamente los if-else o switch sobre el estado.
 *
 * POO — Encapsulamiento:
 * Los campos son privados. El estado interno solo cambia a través de
 * métodos semánticos que representan hechos del negocio.
 *
 * Principio de Responsabilidad Única (SRP):
 * Pedido solo conoce las reglas de negocio del dominio de pedidos.
 * No sabe nada de Kafka, RabbitMQ, JPA ni HTTP.
 *
 * Inversión de Dependencias (DIP):
 * Pedido no depende de ningún framework. Es POJO puro.
 * Los adaptadores (JPA, Kafka) dependen de Pedido, no al revés.
 */
public class Pedido {

    // =========================================================
    // ESTADO INTERNO — privado, sin setters públicos directos
    // =========================================================

    private final String id;
    private final String customerName;
    private final String product;
    private final int    quantity;
    private final String address;
    private final Instant creadoEn;

    // Campos mutables solo a través de métodos de negocio
    private String     driverId;
    private String     driverName;
    private Instant    actualizadoEn;

    /**
     * Estado actual del pedido — delegado al Patrón State.
     * Paquete-privado para que los estados concretos puedan mutarlo.
     */
    OrderState estadoActual;

    // =========================================================
    // CONSTRUCTOR — Fail-Fast
    // Solo acepta los datos iniciales para crear un pedido.
    // El estado comienza siempre en CREADO.
    // =========================================================

    /**
     * Crea un nuevo pedido en estado CREADO.
     * Valida todos los invariantes de negocio inmediatamente.
     *
     * @param id           UUID único del pedido (generado por order_api)
     * @param customerName Nombre del cliente (no vacío)
     * @param product      Producto pedido (no vacío)
     * @param quantity     Cantidad (mayor que cero)
     * @param address      Dirección de entrega (no vacía)
     */
    public Pedido(String id, String customerName, String product,
                  int quantity, String address) {

        // Validaciones Fail-Fast — el objeto no existe si los datos son inválidos
        Objects.requireNonNull(id, "id no puede ser nulo");
        Objects.requireNonNull(customerName, "customerName no puede ser nulo");
        Objects.requireNonNull(product, "product no puede ser nulo");
        Objects.requireNonNull(address, "address no puede ser nulo");

        if (id.isBlank()) {
            throw new IllegalArgumentException("id no puede estar vacío");
        }
        if (customerName.isBlank()) {
            throw new IllegalArgumentException("customerName no puede estar vacío");
        }
        if (product.isBlank()) {
            throw new IllegalArgumentException("product no puede estar vacío");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity debe ser mayor que cero, recibido: " + quantity);
        }
        if (address.isBlank()) {
            throw new IllegalArgumentException("address no puede estar vacía");
        }

        this.id           = id;
        this.customerName = customerName;
        this.product      = product;
        this.quantity     = quantity;
        this.address      = address;
        this.creadoEn     = Instant.now();
        this.actualizadoEn = this.creadoEn;

        // Todo pedido nace en estado CREADO
        this.estadoActual = new EstadoCreado();
    }

    // =========================================================
    // MÉTODOS DE NEGOCIO — Patrón State en acción
    // =========================================================

    /**
     * Asigna un repartidor al pedido.
     * Delega al estado actual, que valida si la transición es legal.
     * Solo válido en estado CREADO.
     *
     * @throws IllegalStateException si el pedido no está en estado CREADO
     */
    public void asignarRepartidor(String driverId, String driverName) {
        // El estado actual decide si esto es válido
        estadoActual.asignarRepartidor(this, driverId, driverName);
    }

    /**
     * Marca que el repartidor inició el recorrido.
     * Solo válido en estado ASIGNADO.
     *
     * @throws IllegalStateException si el pedido no está en estado ASIGNADO
     */
    public void iniciarEntrega() {
        estadoActual.iniciarEntrega(this);
        this.actualizadoEn = Instant.now();
    }

    /**
     * Registra que el repartidor está llegando al destino.
     * Solo válido en estado EN_CAMINO.
     *
     * @throws IllegalStateException si el pedido no está en estado EN_CAMINO
     */
    public void registrarLlegada() {
        estadoActual.registrarLlegada(this);
        this.actualizadoEn = Instant.now();
    }

    /**
     * Completa el ciclo de vida: el pedido fue entregado.
     * Solo válido en estado LLEGANDO.
     *
     * @throws IllegalStateException si el pedido no está en estado LLEGANDO
     */
    public void completarEntrega() {
        estadoActual.completarEntrega(this);
        this.actualizadoEn = Instant.now();
    }

    // =========================================================
    // MÉTODO DE APOYO — usado internamente por los estados
    // Paquete-privado intencionalmente (no expuesto afuera)
    // =========================================================

    /**
     * Aplica la asignación del repartidor.
     * Solo los estados del dominio deben llamar este método.
     * Protected: accesible desde los estados concretos del paquete state.
     */
    public void aplicarAsignacion(String driverId, String driverName) {
        Objects.requireNonNull(driverId, "driverId no puede ser nulo en la asignación");
        Objects.requireNonNull(driverName, "driverName no puede ser nulo en la asignación");

        if (driverId.isBlank()) {
            throw new IllegalArgumentException("driverId no puede estar vacío");
        }
        if (driverName.isBlank()) {
            throw new IllegalArgumentException("driverName no puede estar vacío");
        }

        this.driverId      = driverId;
        this.driverName    = driverName;
        this.actualizadoEn = Instant.now();
    }

    /**
     * Cambia el estado actual. Solo los estados concretos deben llamar esto.
     * Public para que los estados en el subpaquete 'state' puedan invocarlo.
     * No es un setter indiscriminado — el único contrato es cambiar el estado interno.
     */
    public void setEstado(OrderState nuevoEstado) {
        this.estadoActual = nuevoEstado;
    }

    // =========================================================
    // CONSULTAS — getters de solo lectura
    // No hay setters públicos. El estado solo cambia por métodos de negocio.
    // =========================================================

    public String getId()           { return id; }
    public String getCustomerName() { return customerName; }
    public String getProduct()      { return product; }
    public int    getQuantity()     { return quantity; }
    public String getAddress()      { return address; }
    public String getDriverId()     { return driverId; }
    public String getDriverName()   { return driverName; }
    public Instant getCreadoEn()    { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }

    /**
     * Nombre del estado actual para persistencia y logs.
     */
    public String getNombreEstado() {
        return estadoActual.nombre();
    }

    @Override
    public String toString() {
        return "Pedido{id='" + id + "', customer='" + customerName +
               "', estado='" + getNombreEstado() + "', driver='" + driverId + "'}";
    }
}
