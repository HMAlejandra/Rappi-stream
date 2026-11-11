package com.rappistream.dashboardbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuración WebSocket con STOMP.
 *
 * STOMP (Simple Text Oriented Messaging Protocol) es un protocolo
 * de mensajería sobre WebSocket. Spring lo soporta nativamente.
 *
 * Por qué STOMP sobre WebSocket puro:
 * - Tiene concepto de "topics" (destinos de suscripción)
 * - Spring maneja automáticamente el broadcast a múltiples clientes
 * - El cliente JS usa stomp.js — más robusto que WebSocket nativo
 *
 * Configuración:
 * - Endpoint de conexión: /ws
 * - Prefijo de mensajes del servidor: /topic
 * - El aggregator publica en: /topic/orders
 * - El cliente JS se suscribe a: /topic/orders
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // El broker simple de Spring maneja /topic/**
        registry.enableSimpleBroker("/topic");
        // Prefijo para mensajes que van al servidor (no usado en este MVP)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // SockJS como fallback para navegadores sin WebSocket nativo
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
