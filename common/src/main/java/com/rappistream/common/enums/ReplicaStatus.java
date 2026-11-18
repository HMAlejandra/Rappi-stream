package com.rappistream.common.enums;

/**
 * Estado del semáforo de una réplica de worker.
 *
 * Patrón State ligero: cada constante encapsula su luz y color sin if/else
 * anidados. El color alimenta el semáforo visual del dashboard en vivo.
 *   ACTIVE  → verde  (procesando)
 *   ALERT   → ámbar  (latencia alta / alta carga)
 *   IDLE    → gris   (inactiva)
 */
public enum ReplicaStatus {

    ACTIVE("verde", "#34C759"),
    ALERT("ambar", "#FFCC00"),
    IDLE("gris", "#555555");

    private final String luz;
    private final String color;

    ReplicaStatus(String luz, String color) {
        this.luz = luz;
        this.color = color;
    }

    public String luz() {
        return luz;
    }

    /** Color hex para el semáforo del dashboard. */
    public String color() {
        return color;
    }
}
