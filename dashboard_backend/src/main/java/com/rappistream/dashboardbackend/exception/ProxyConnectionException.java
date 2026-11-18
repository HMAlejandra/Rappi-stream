package com.rappistream.dashboardbackend.exception;

/**
 * Se lanza cuando el backend no puede conectarse con un microservicio upstream.
 */
public class ProxyConnectionException extends BackendException {

    public ProxyConnectionException(String servicio, String url) {
        super("No se pudo conectar con '" + servicio + "' en: " + url);
    }
}
