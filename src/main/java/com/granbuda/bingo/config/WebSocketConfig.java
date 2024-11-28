// src/main/java/com/granbuda/bingo/config/WebSocketConfig.java
package com.granbuda.bingo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Configurar el broker simple con el prefijo "/topic"
        config.enableSimpleBroker("/topic");
        // Configurar el prefijo para los destinos de aplicación
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:3000") // Usar patrones en lugar de "*"
                //.setAllowedOrigins("http://localhost:5173") // Alternativamente, usar orígenes específicos
                .withSockJS(); // Soporte para navegadores que no soportan WebSockets
    }
}
