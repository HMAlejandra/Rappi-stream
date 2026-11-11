package com.rappistream.dashboardaggregator.domain;

import com.rappistream.common.enums.OrderStatus;

import java.time.Instant;

/**
 * Vista materializada en memoria del estado actual de un pedido.
 *
 * Sistemas Distribuidos — Consumidor Stateful:
 * El aggregator mantiene un Map<orderId, OrderSnapshot> en memoria.
 * Cada evento de Kafka actualiza el snapshot correspondiente.
 * Esta es la "vista materializada" del estado del sistema.
 *
 * POO — Modelo Rico:
 * No es anémico. Tiene métodos semánticos que aplican
 * cada tipo de evento al estado del snapshot.
 *
 * No es una entidad JPA — es un objeto de dominio puro.
 * Se convierte a OrderStatusDTO para enviarlo al dashboard_backend.
 */
public class OrderSnapshot {

    private final String  orderId;
    private final String  customerName;
    private final String  product;
    private final int     quantity;
    private final String  address;
    private final Instant creadoEn;

    private String      driverId;
    private String      driverName;
    private OrderStatus status;
    private Instant     lastUpdated;

    public OrderSnapshot(String orderId, String customerName, String product,
                         int quantity, String address) {
        this.orderId      = orderId;
        this.customerName = customerName;
        this.product      = product;
        this.quantity     = quantity;
        this.address      = address;
        this.status       = OrderStatus.CREADO;
        this.creadoEn     = Instant.now();
        this.lastUpdated  = this.creadoEn;
    }

    // =========================================================
    // Métodos semánticos — cada evento actualiza el snapshot
    // =========================================================

    public void aplicarDriverAssigned(String driverId, String driverName) {
        this.driverId    = driverId;
        this.driverName  = driverName;
        this.status      = OrderStatus.ASIGNADO;
        this.lastUpdated = Instant.now();
    }

    public void aplicarDeliveryStarted() {
        this.status      = OrderStatus.EN_CAMINO;
        this.lastUpdated = Instant.now();
    }

    public void aplicarDriverArriving() {
        this.status      = OrderStatus.LLEGANDO;
        this.lastUpdated = Instant.now();
    }

    public void aplicarOrderDelivered() {
        this.status      = OrderStatus.ENTREGADO;
        this.lastUpdated = Instant.now();
    }

    // =========================================================
    // Consultas
    // =========================================================

    public String      getOrderId()      { return orderId; }
    public String      getCustomerName() { return customerName; }
    public String      getProduct()      { return product; }
    public int         getQuantity()     { return quantity; }
    public String      getAddress()      { return address; }
    public String      getDriverId()     { return driverId; }
    public String      getDriverName()   { return driverName; }
    public OrderStatus getStatus()       { return status; }
    public Instant     getCreadoEn()     { return creadoEn; }
    public Instant     getLastUpdated()  { return lastUpdated; }
}
