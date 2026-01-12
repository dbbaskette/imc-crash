package com.insurancemegacorp.crash.orchestrator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration for real-time UI updates.
 * Enables STOMP over WebSocket for broadcasting agent status and customer detection events.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable a simple in-memory message broker
        // Clients subscribe to /topic/* to receive messages
        registry.enableSimpleBroker("/topic");
        
        // Application destination prefix (not used for broadcasting, but required)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register "/ws-crash" endpoint for WebSocket connections
        // withSockJS() provides fallback options for browsers that don't support WebSocket
        registry.addEndpoint("/ws-crash")
                .setAllowedOriginPatterns("*")  // Allow connections from any origin (for dev)
                .withSockJS();
    }
}
