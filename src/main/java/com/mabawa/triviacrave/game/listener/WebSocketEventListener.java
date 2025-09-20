package com.mabawa.triviacrave.game.listener;

import com.mabawa.triviacrave.common.security.JwtAuthenticationToken;
import com.mabawa.triviacrave.game.service.impl.WebSocketMessageServiceImpl;
import com.mabawa.triviacrave.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;

/**
 * WebSocket event listener to handle connection, disconnection, and subscription events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final WebSocketMessageServiceImpl webSocketMessageService;
    private final UserService userService;
    
    /**
     * Handle WebSocket connection events
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        try {
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
            String sessionId = headerAccessor.getSessionId();
            Principal user = headerAccessor.getUser();
            
            if (user != null && sessionId != null) {
                log.info("WebSocket connection established: sessionId={}, user={}", sessionId, user.getName());
                
                // Extract user information for session registration
                // Note: You may need to implement user lookup by username/email
                // For now, we'll log the connection and defer registration to subscription events
                
            } else {
                log.warn("WebSocket connection without proper authentication: sessionId={}", sessionId);
            }
        } catch (Exception e) {
            log.error("Error handling WebSocket connection event", e);
        }
    }
    
    /**
     * Handle WebSocket disconnection events
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        try {
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
            String sessionId = headerAccessor.getSessionId();
            Principal user = headerAccessor.getUser();
            
            if (sessionId != null) {
                log.info("WebSocket connection closed: sessionId={}, user={}", 
                        sessionId, user != null ? user.getName() : "unknown");
                
                // Unregister the session
                webSocketMessageService.unregisterUserSession(sessionId);
                
                // Notify about disconnection if user was authenticated
                if (user != null) {
                    webSocketMessageService.notifyConnectionStatus(user.getName(), false);
                }
            }
        } catch (Exception e) {
            log.error("Error handling WebSocket disconnection event", e);
        }
    }
    
    /**
     * Handle WebSocket subscription events
     */
    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        try {
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
            String sessionId = headerAccessor.getSessionId();
            String destination = headerAccessor.getDestination();
            Principal user = headerAccessor.getUser();
            
            if (sessionId != null && destination != null && user != null) {
                log.info("WebSocket subscription: sessionId={}, user={}, destination={}", 
                        sessionId, user.getName(), destination);
                
                // Extract game ID from destination if it's a game-related subscription
                Long gameId = extractGameIdFromDestination(destination);

                // Register user session with game context
                if (gameId != null) {
                    try {
                        Long userId = extractUserIdFromPrincipal(user);
                        if (userId != null) {
                            log.info("User {} (ID: {}) subscribed to game {}", user.getName(), userId, gameId);
                            webSocketMessageService.registerUserSession(sessionId, userId, gameId);
                        } else {
                            log.warn("Could not extract userId from principal for session: {}", sessionId);
                        }
                    } catch (Exception e) {
                        log.error("Error registering user session: sessionId={}, gameId={}", sessionId, gameId, e);
                    }
                }
                
                // Notify about connection status
                webSocketMessageService.notifyConnectionStatus(user.getName(), true);
            }
        } catch (Exception e) {
            log.error("Error handling WebSocket subscription event", e);
        }
    }
    
    /**
     * Handle WebSocket unsubscription events
     */
    @EventListener
    public void handleWebSocketUnsubscribeListener(SessionUnsubscribeEvent event) {
        try {
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
            String sessionId = headerAccessor.getSessionId();
            String destination = headerAccessor.getDestination();
            Principal user = headerAccessor.getUser();
            
            if (sessionId != null && destination != null && user != null) {
                log.info("WebSocket unsubscription: sessionId={}, user={}, destination={}", 
                        sessionId, user.getName(), destination);
                
                // Extract game ID from destination
                Long gameId = extractGameIdFromDestination(destination);
                if (gameId != null) {
                    log.info("User {} unsubscribed from game {}", user.getName(), gameId);
                }
            }
        } catch (Exception e) {
            log.error("Error handling WebSocket unsubscription event", e);
        }
    }
    
    /**
     * Extract game ID from WebSocket destination path
     * Expected format: /topic/game/{gameId} or /user/queue/game/{gameId}
     */
    private Long extractGameIdFromDestination(String destination) {
        try {
            if (destination != null && destination.contains("/game/")) {
                String[] parts = destination.split("/game/");
                if (parts.length >= 2) {
                    String gameIdStr = parts[1].split("/")[0]; // Get first part after /game/
                    return Long.parseLong(gameIdStr);
                }
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid game ID in destination: {}", destination);
        }
        return null;
    }

    /**
     * Extract user ID from Principal
     * Handles both JwtAuthenticationToken and username-based lookup
     */
    private Long extractUserIdFromPrincipal(Principal principal) {
        if (principal == null) {
            return null;
        }

        try {
            // First, try to extract from JwtAuthenticationToken if available
            if (principal instanceof JwtAuthenticationToken) {
                JwtAuthenticationToken jwtToken = (JwtAuthenticationToken) principal;
                return jwtToken.getUserId();
            }

            // Fallback: lookup user by username/email
            String username = principal.getName();
            if (username != null && !username.isEmpty()) {
                var user = userService.getUserByEmail(username);
                if (user != null) {
                    log.debug("Found user by email lookup: {} -> {}", username, user.getId());
                    return user.getId();
                } else {
                    log.warn("User not found by email/username: {}", username);
                    return null;
                }
            }
        } catch (Exception e) {
            log.error("Error extracting userId from principal: {}", principal.getName(), e);
        }

        return null;
    }
}