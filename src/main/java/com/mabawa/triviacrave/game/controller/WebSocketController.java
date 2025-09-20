package com.mabawa.triviacrave.game.controller;

import com.mabawa.triviacrave.common.security.JwtAuthenticationToken;
import com.mabawa.triviacrave.game.dto.websocket.MessageType;
import com.mabawa.triviacrave.game.dto.websocket.WebSocketMessage;
import com.mabawa.triviacrave.game.service.impl.WebSocketMessageServiceImpl;
import com.mabawa.triviacrave.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket controller for handling chat and presence functionality in the trivia game.
 *
 * IMPORTANT: This controller is responsible ONLY for:
 * - Chat messaging between players
 * - Session registration and presence tracking
 * - Connection monitoring (ping/pong)
 *
 * Game logic operations (join game, readiness, submit answers) should be handled
 * via GraphQL mutations, not through WebSocket messaging.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final WebSocketMessageServiceImpl webSocketMessageService;
    private final UserService userService;
    
    /**
     * Handle game subscription - register user session for chat and presence when they subscribe to a game.
     * This establishes the WebSocket connection for receiving real-time updates like chat messages
     * and presence notifications, but does NOT handle game joining logic.
     *
     * Game joining should be done via GraphQL mutation, which will then trigger
     * appropriate WebSocket notifications to other players.
     */
    @SubscribeMapping("/topic/game/{gameId}")
    public void subscribeToGame(@DestinationVariable Long gameId,
                               SimpMessageHeaderAccessor headerAccessor,
                               Principal principal) {
        try {
            String sessionId = headerAccessor.getSessionId();

            if (principal != null && sessionId != null) {
                log.info("User {} subscribing to game {} for chat/presence (session: {})",
                         principal.getName(), gameId, sessionId);

                // Register user session for this game
                try {
                    Long userId = extractUserIdFromPrincipal(principal);
                    if (userId != null) {
                        log.info("Registering user {} (ID: {}) session for game {}", principal.getName(), userId, gameId);
                        webSocketMessageService.registerUserSession(sessionId, userId, gameId);
                    } else {
                        log.warn("Could not extract userId from principal for session: {}", sessionId);
                    }
                } catch (Exception e) {
                    log.error("Error registering user session: sessionId={}, gameId={}", sessionId, gameId, e);
                }

                // Send connection confirmation to the subscribing user
                WebSocketMessage connectionMessage = WebSocketMessage.builder()
                        .type(MessageType.CONNECTION_STATUS.getValue())
                        .gameId(gameId)
                        .data(Map.of(
                                "status", "connected",
                                "message", "Connected to game chat and presence system",
                                "gameId", gameId,
                                "userName", principal.getName(),
                                "sessionId", sessionId
                        ))
                        .build();

                webSocketMessageService.sendPrivateMessage(principal.getName(), "/queue/personal", connectionMessage);
            } else {
                log.warn("Invalid subscription attempt - missing principal or sessionId for gameId: {}", gameId);
            }
        } catch (Exception e) {
            log.error("Error handling game subscription for gameId: {}", gameId, e);
        }
    }
    
    /**
     * Handle chat messages during game.
     * This allows players to communicate with each other in real-time.
     * Chat messages are broadcast to all players currently subscribed to the game topic.
     */
    @MessageMapping("/game/{gameId}/chat")
    public void sendChatMessage(@DestinationVariable Long gameId,
                               @Payload Map<String, Object> payload,
                               Principal principal) {
        try {
            if (principal != null) {
                String message = (String) payload.get("message");

                if (message != null && !message.trim().isEmpty()) {
                    log.info("Chat message from {} in game {}: {}", principal.getName(), gameId, message);

                    WebSocketMessage chatMessage = WebSocketMessage.builder()
                            .type("chat") // Using simple "chat" type for chat messages
                            .gameId(gameId)
                            .senderName(principal.getName())
                            .data(Map.of(
                                    "playerName", principal.getName(),
                                    "message", message.trim()
                            ))
                            .build();

                    // Broadcast to all players subscribed to this game
                    webSocketMessageService.broadcastToGame(gameId, "chat", chatMessage);
                } else {
                    log.debug("Empty chat message received from {} in game {}", principal.getName(), gameId);
                }
            } else {
                log.warn("Chat message received without authenticated principal for gameId: {}", gameId);
            }
        } catch (Exception e) {
            log.error("Error handling chat message for gameId: {}", gameId, e);
        }
    }
    
    /**
     * Handle heartbeat/ping messages to keep WebSocket connections alive.
     * This is used for connection monitoring and ensuring the session remains active.
     * Responds with a pong message to confirm the connection is still healthy.
     */
    @MessageMapping("/ping")
    public void handlePing(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        try {
            String sessionId = headerAccessor.getSessionId();

            if (principal != null && sessionId != null) {
                // Update session activity tracking
                log.debug("Ping received from user {} (session: {})", principal.getName(), sessionId);

                // TODO: Update session activity in session management service
                // This could be used for presence tracking and automatic cleanup

                WebSocketMessage pongMessage = WebSocketMessage.builder()
                        .type("pong")
                        .data(Map.of(
                                "timestamp", System.currentTimeMillis(),
                                "message", "pong",
                                "sessionId", sessionId
                        ))
                        .build();

                webSocketMessageService.sendPrivateMessage(principal.getName(), "/queue/personal", pongMessage);
            } else {
                log.warn("Ping received without valid principal or sessionId");
            }
        } catch (Exception e) {
            log.error("Error handling ping from user: {}",
                     principal != null ? principal.getName() : "unknown", e);
        }
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