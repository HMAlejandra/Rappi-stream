package com.rappistream.common.enums;

/**
 * Canal de trabajo de una réplica de worker.
 *
 * Patrón Strategy: notification_router y los workers usan este tipo para
 * seleccionar el canal correcto. El enum encapsula su etiqueta legible
 * (no es un contenedor anémico de constantes).
 */
public enum WorkerType {

    SMS("SMS"),
    PUSH("Push");

    private final String etiqueta;

    WorkerType(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Etiqueta legible para el semáforo del dashboard. */
    public String etiqueta() {
        return etiqueta;
    }
}
