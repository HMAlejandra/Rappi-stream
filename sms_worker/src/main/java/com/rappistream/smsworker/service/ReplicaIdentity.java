package com.rappistream.smsworker.service;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/**
 * Identidad de la réplica: resuelve y cachea el hostname del contenedor.
 *
 * Clase enriquecida — encapsula la resolución y maneja el fallo con un
 * fallback determinista, de modo que cada réplica tenga un id estable y
 * único para el semáforo del dashboard.
 */
@Component
public class ReplicaIdentity {

    private final String id;

    public ReplicaIdentity() {
        this.id = resolverHostname();
    }

    private String resolverHostname() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            if (host != null && !host.isBlank()) return host;
        } catch (UnknownHostException ignored) {
            // fallback abajo
        }
        return "sms-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String id() {
        return id;
    }
}
