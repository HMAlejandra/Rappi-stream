package com.rappistream.common.enums;

/**
 * Estado del ciclo de vida de una ráfaga de pedidos.
 *
 * Patrón State ligero: cada constante encapsula su etiqueta y si es terminal,
 * evitando condicionales anidados en el dominio.
 *   RUNNING   → en curso
 *   COMPLETED → todos los pedidos procesados (terminal)
 *   ALERT     → estancada (sin progreso prolongado)
 */
public enum BurstStatus {

    RUNNING("En curso"),
    COMPLETED("Completada"),
    ALERT("Estancada");

    private final String etiqueta;

    BurstStatus(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** Estado terminal: no admite más transiciones. */
    public boolean esTerminal() {
        return this == COMPLETED;
    }
}
