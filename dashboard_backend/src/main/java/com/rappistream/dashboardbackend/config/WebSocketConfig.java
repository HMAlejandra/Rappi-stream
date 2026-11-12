package com.rappistream.dashboardbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuración WebSocket con STOMP — sin SockJS.
 *
 * SockJS usa el evento 'unload' que está bloqueado en browsers modernos
 * por la Permissions Policy. Se reemplaza por WebSocket nativo directo.
 *
 * El cliente JS usa STOMP sobre WebSocket nativo (sin SockJS):
 *   const socket = new WebSocket("ws://localhost:8087/ws");
 *   const client = Stomp.over(socket);
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Sin withSockJS() — WebSocket nativo puro, sin el evento 'unload'
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }
}
