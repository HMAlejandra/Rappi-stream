package com.rappistream.orderapi.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * DTO de respuesta para errores HTTP.
 *
 * Mismo patrón que el proyecto Hotel:
 * deError()      → errores simples (negocio, no encontrado)
 * deValidacion() → errores de Bean Validation con mapa campo→mensaje
 */
public record ErrorResponse(
        Instant             timestamp,
        int                 codigoHttp,
        String              estado,
        String              mensaje,
        String              ruta,
        Map<String, String> detallesValidacion
) {

    public static ErrorResponse deError(int codigoHttp, String estado,
                                         String mensaje, String ruta) {
        return new ErrorResponse(Instant.now(), codigoHttp, estado, mensaje, ruta, null);
    }

    public static ErrorResponse deValidacion(int codigoHttp, String estado, String mensaje,
                                              String ruta, Map<String, String> errores) {
        return new ErrorResponse(Instant.now(), codigoHttp, estado, mensaje, ruta, errores);
    }
}
