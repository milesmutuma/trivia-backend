package com.mabawa.triviacrave.game.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * WebSocket event handler to manage connection and disconnection events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventHandler {
    
    private final WebSocketMessageService webSocketMessageService;
    
    /**
     * Handle WebSocket connection events
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        // Try to get authentication information
        Authentication auth = (Authentication) headerAccessor.getUser();
        String username = auth != null ? auth.getName() : "Anonymous";
        
        log.info("WebSocket connection established - Session: {}, User: {}", sessionId, username);
        
        // Notify about connection status if user is authenticated
        if (auth != null && auth.isAuthenticated()) {
            try {
                webSocketMessageService.notifyConnectionStatus(username, true);
            } catch (Exception e) {
                log.warn("Failed to notify connection status for user {}: {}", username, e.getMessage());
            }
        }
    }
    
    /**
     * Handle WebSocket disconnection events
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        // Try to get authentication information
        Authentication auth = (Authentication) headerAccessor.getUser();
        String username = auth != null ? auth.getName() : "Anonymous";
        
        log.info("WebSocket connection closed - Session: {}, User: {}", sessionId, username);
        
        // Notify about disconnection status if user is authenticated
        if (auth != null && auth.isAuthenticated()) {
            try {
                webSocketMessageService.notifyConnectionStatus(username, false);
                
                // Here you could also handle game-specific disconnection logic
                // For example, updating player status in active games
                // This might involve calling GameService to handle player disconnections
            } catch (Exception e) {
                log.warn("Failed to notify disconnection status for user {}: {}", username, e.getMessage());
            }
        }
    }
}