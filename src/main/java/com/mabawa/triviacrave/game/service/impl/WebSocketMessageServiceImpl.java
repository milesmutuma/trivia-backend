package com.mabawa.triviacrave.game.service.impl;

import com.mabawa.triviacrave.game.dto.websocket.MessageType;
import com.mabawa.triviacrave.game.dto.websocket.UserSession;
import com.mabawa.triviacrave.game.dto.websocket.WebSocketMessage;
import com.mabawa.triviacrave.game.entity.Game;
import com.mabawa.triviacrave.game.entity.GamePlayer;
import com.mabawa.triviacrave.game.service.WebSocketMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of WebSocketMessageService for real-time game messaging
 * Provides complete WebSocket messaging functionality with session management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketMessageServiceImpl implements WebSocketMessageService {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    // Thread-safe session storage
    private final Map<String, UserSession> sessionMap = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> userIdToSessionsMap = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> gameIdToSessionsMap = new ConcurrentHashMap<>();
    
    // Session timeout in minutes
    private static final int SESSION_TIMEOUT_MINUTES = 30;

    @Override
    public void notifyPlayerJoined(Long gameId, GamePlayer player) {
        log.debug("Player joined game: gameId={}, playerId={}", gameId, player.getUser().getId());
        
        try {
            WebSocketMessage message = WebSocketMessage.builder()
                    .type(MessageType.PLAYER_JOINED.getValue())
                    .gameId(gameId)
                    .senderId(player.getUser().getId())
                    .senderName(player.getUser().getDisplayName())
                    .data(Map.of(
                            "playerId", player.getUser().getId(),
                            "playerName", player.getUser().getDisplayName(),
                            "joinedAt", LocalDateTime.now()
                    ))
                    .messageId(UUID.randomUUID().toString())
                    .build();
            
            broadcastToGame(gameId, MessageType.PLAYER_JOINED.getValue(), message);
            log.info("Successfully notified player joined: gameId={}, playerId={}", gameId, player.getUser().getId());
        } catch (Exception e) {
            log.error("Failed to notify player joined: gameId={}, playerId={}", gameId, player.getUser().getId(), e);
        }
    }

    @Override
    public void notifyPlayerLeft(Long gameId, GamePlayer player) {
        log.debug("Player left game: gameId={}, playerId={}", gameId, player.getUser().getId());
        
        try {
            WebSocketMessage message = WebSocketMessage.builder()
                    .type(MessageType.PLAYER_LEFT.getValue())
                    .gameId(gameId)
                    .senderId(player.getUser().getId())
                    .senderName(player.getUser().getDisplayName())
                    .data(Map.of(
                            "playerId", player.getUser().getId(),
                            "playerName", player.getUser().getDisplayName(),
                            "leftAt", LocalDateTime.now()
                    ))
                    .messageId(UUID.randomUUID().toString())
                    .build();
            
            broadcastToGame(gameId, MessageType.PLAYER_LEFT.getValue(), message);
            log.info("Successfully notified player left: gameId={}, playerId={}", gameId, player.getUser().getId());
        } catch (Exception e) {
            log.error("Failed to notify player left: gameId={}, playerId={}", gameId, player.getUser().getId(), e);
        }
    }

    @Override
    public void notifyGameStarted(Game game) {
        log.debug("Game started: gameId={}", game.getId());
        
        try {
            WebSocketMessage message = WebSocketMessage.builder()
                    .type(MessageType.GAME_STARTED.getValue())
                    .gameId(game.getId())
                    .data(Map.of(
                            "gameId", game.getId(),
                            "gameMode", game.getGameMode().toString(),
                            "startedAt", LocalDateTime.now(),
                            "status", game.getStatus().toString()
                    ))
                    .messageId(UUID.randomUUID().toString())
                    .priority(WebSocketMessage.MessagePriority.HIGH)
                    .build();
            
            broadcastToGame(game.getId(), MessageType.GAME_STARTED.getValue(), message);
            log.info("Successfully notified game started: gameId={}", game.getId());
        } catch (Exception e) {
            log.error("Failed to notify game started: gameId={}", game.getId(), e);
        }
    }

    @Override
    public void sendQuestionToPlayers(Long gameId, Object question) {
        log.debug("Sending question to players: gameId={}", gameId);
        
        try {
            WebSocketMessage message = WebSocketMessage.builder()
                    .type(MessageType.QUESTION_SENT.getValue())
                    .gameId(gameId)
                    .data(question)
                    .messageId(UUID.randomUUID().toString())
                    .priority(WebSocketMessage.MessagePriority.HIGH)
                    .build();
            
            broadcastToGame(gameId, MessageType.QUESTION_SENT.getValue(), message);
            log.info("Successfully sent question to players: gameId={}", gameId);
        } catch (Exception e) {
            log.error("Failed to send question to players: gameId={}", gameId, e);
        }
    }

    @Override
    public void notifyScoreUpdate(Long gameId, Object scoreUpdate) {
        log.debug("Score update for game: gameId={}", gameId);
        
        try {
            WebSocketMessage message = WebSocketMessage.builder()
                    .type(MessageType.SCORE_UPDATE.getValue())
                    .gameId(gameId)
                    .data(scoreUpdate)
                    .messageId(UUID.randomUUID().toString())
                    .build();
            
            broadcastToGame(gameId, MessageType.SCORE_UPDATE.getValue(), message);
            log.info("Successfully sent score update: gameId={}", gameId);
        } catch (Exception e) {
            log.error("Failed to send score update: gameId={}", gameId, e);
        }
    }

    @Override
    public void notifyGameEnded(Game game, Object results) {
        log.debug("Game ended: gameId={}", game.getId());
        
        try {
            WebSocketMessage message = WebSocketMessage.builder()
                    .type(MessageType.GAME_ENDED.getValue())
                    .gameId(game.getId())
                    .data(Map.of(
                            "gameId", game.getId(),
                            "endedAt", LocalDateTime.now(),
                            "finalResults", results,
                            "status", game.getStatus().toString()
                    ))
                    .messageId(UUID.randomUUID().toString())
                    .priority(WebSocketMessage.MessagePriority.HIGH)
                    .build();
            
            broadcastToGame(game.getId(), MessageType.GAME_ENDED.getValue(), message);
            
            // Clean up game sessions after game ends
            cleanupGameSessions(game.getId());
            
            log.info("Successfully notified game ended: gameId={}", game.getId());
        } catch (Exception e) {
            log.error("Failed to notify game ended: gameId={}", game.getId(), e);
        }
    }

    @Override
    public void sendPrivateMessage(String username, String destination, Object message) {
        log.debug("Sending private message to: {}", username);
        
        try {
            WebSocketMessage wsMessage = WebSocketMessage.builder()
                    .type(MessageType.PRIVATE_MESSAGE.getValue())
                    .data(message)
                    .messageId(UUID.randomUUID().toString())
                    .build();
            
            String dest = destination != null ? destination : "/queue/" + username;
            messagingTemplate.convertAndSendToUser(username, dest, wsMessage);
            
            log.info("Successfully sent private message to: {}", username);
        } catch (Exception e) {
            log.error("Failed to send private message to: {}", username, e);
        }
    }

    @Override
    public void notifyGameStateChange(Long gameId, Game.GameStatus status) {
        log.debug("Game state changed: gameId={}, status={}", gameId, status);
        
        try {
            WebSocketMessage message = WebSocketMessage.builder()
                    .type(MessageType.GAME_STATE_CHANGE.getValue())
                    .gameId(gameId)
                    .data(Map.of(
                            "gameId", gameId,
                            "newStatus", status.toString(),
                            "changedAt", LocalDateTime.now()
                    ))
                    .messageId(UUID.randomUUID().toString())
                    .priority(WebSocketMessage.MessagePriority.HIGH)
                    .build();
            
            broadcastToGame(gameId, MessageType.GAME_STATE_CHANGE.getValue(), message);
            log.info("Successfully notified game state change: gameId={}, status={}", gameId, status);
        } catch (Exception e) {
            log.error("Failed to notify game state change: gameId={}, status={}", gameId, status, e);
        }
    }

    @Override
    public void notifyPlayerReadiness(Long gameId, GamePlayer player) {
        log.debug("Player readiness update: gameId={}, playerId={}", gameId, player.getUser().getId());
        
        try {
            WebSocketMessage message = WebSocketMessage.builder()
                    .type(MessageType.PLAYER_READINESS.getValue())
                    .gameId(gameId)
                    .senderId(player.getUser().getId())
                    .senderName(player.getUser().getDisplayName())
                    .data(Map.of(
                            "playerId", player.getUser().getId(),
                            "playerName", player.getUser().getDisplayName(),
                            "isReady", player.getIsReady(),
                            "updatedAt", LocalDateTime.now()
                    ))
                    .messageId(UUID.randomUUID().toString())
                    .build();
            
            broadcastToGame(gameId, MessageType.PLAYER_READINESS.getValue(), message);
            log.info("Successfully notified player readiness: gameId={}, playerId={}, ready={}", 
                    gameId, player.getUser().getId(), player.getIsReady());
        } catch (Exception e) {
            log.error("Failed to notify player readiness: gameId={}, playerId={}", 
                    gameId, player.getUser().getId(), e);
        }
    }

    @Override
    public void broadcastLeaderboard(Long gameId, Object leaderboard) {
        log.debug("Broadcasting leaderboard: gameId={}", gameId);
        
        try {
            WebSocketMessage message = WebSocketMessage.builder()
                    .type(MessageType.LEADERBOARD_UPDATE.getValue())
                    .gameId(gameId)
                    .data(leaderboard)
                    .messageId(UUID.randomUUID().toString())
                    .build();
            
            broadcastToGame(gameId, MessageType.LEADERBOARD_UPDATE.getValue(), message);
            log.info("Successfully broadcast leaderboard: gameId={}", gameId);
        } catch (Exception e) {
            log.error("Failed to broadcast leaderboard: gameId={}", gameId, e);
        }
    }

    @Override
    public void notifyConnectionStatus(String username, boolean connected) {
        log.debug("Connection status update: username={}, connected={}", username, connected);
        
        try {
            WebSocketMessage message = WebSocketMessage.builder()
                    .type(MessageType.CONNECTION_STATUS.getValue())
                    .data(Map.of(
                            "username", username,
                            "connected", connected,
                            "timestamp", LocalDateTime.now()
                    ))
                    .messageId(UUID.randomUUID().toString())
                    .build();
            
            // Broadcast to all active sessions
            messagingTemplate.convertAndSend("/topic/connection", message);
            log.info("Successfully notified connection status: username={}, connected={}", username, connected);
        } catch (Exception e) {
            log.error("Failed to notify connection status: username={}, connected={}", username, connected, e);
        }
    }

    @Override
    public void broadcastToGame(Long gameId, String messageType, Object message) {
        log.debug("Broadcasting to game: gameId={}, messageType={}", gameId, messageType);
        
        try {
            String destination = "/topic/game/" + gameId;
            messagingTemplate.convertAndSend(destination, message);
            
            // Update activity for all sessions in this game
            Set<String> gameSessions = gameIdToSessionsMap.get(gameId);
            if (gameSessions != null) {
                gameSessions.forEach(sessionId -> {
                    UserSession session = sessionMap.get(sessionId);
                    if (session != null) {
                        session.updateActivity();
                    }
                });
            }
            
            log.info("Successfully broadcast to game: gameId={}, messageType={}, destination={}", 
                    gameId, messageType, destination);
        } catch (Exception e) {
            log.error("Failed to broadcast to game: gameId={}, messageType={}", gameId, messageType, e);
        }
    }

    @Override
    public void registerUserSession(String sessionKey, Long userId, Long gameId) {
        log.debug("Registering user session: sessionKey={}, userId={}, gameId={}", sessionKey, userId, gameId);
        
        try {
            // Create user session
            UserSession session = UserSession.builder()
                    .sessionId(sessionKey)
                    .userId(userId)
                    .gameId(gameId)
                    .build();
            
            // Store session
            sessionMap.put(sessionKey, session);
            
            // Map user ID to session
            userIdToSessionsMap.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionKey);
            
            // Map game ID to session (if in a game)
            if (gameId != null) {
                gameIdToSessionsMap.computeIfAbsent(gameId, k -> ConcurrentHashMap.newKeySet()).add(sessionKey);
            }
            
            log.info("Successfully registered user session: sessionKey={}, userId={}, gameId={}", 
                    sessionKey, userId, gameId);
        } catch (Exception e) {
            log.error("Failed to register user session: sessionKey={}, userId={}, gameId={}", 
                    sessionKey, userId, gameId, e);
        }
    }

    @Override
    public void unregisterUserSession(String sessionKey) {
        log.debug("Unregistering user session: sessionKey={}", sessionKey);
        
        try {
            UserSession session = sessionMap.remove(sessionKey);
            
            if (session != null) {
                // Remove from user ID mapping
                Set<String> userSessions = userIdToSessionsMap.get(session.getUserId());
                if (userSessions != null) {
                    userSessions.remove(sessionKey);
                    if (userSessions.isEmpty()) {
                        userIdToSessionsMap.remove(session.getUserId());
                    }
                }
                
                // Remove from game ID mapping
                if (session.getGameId() != null) {
                    Set<String> gameSessions = gameIdToSessionsMap.get(session.getGameId());
                    if (gameSessions != null) {
                        gameSessions.remove(sessionKey);
                        if (gameSessions.isEmpty()) {
                            gameIdToSessionsMap.remove(session.getGameId());
                        }
                    }
                }
                
                log.info("Successfully unregistered user session: sessionKey={}, userId={}", 
                        sessionKey, session.getUserId());
            } else {
                log.warn("Attempted to unregister non-existent session: sessionKey={}", sessionKey);
            }
        } catch (Exception e) {
            log.error("Failed to unregister user session: sessionKey={}", sessionKey, e);
        }
    }
    
    /**
     * Additional utility methods for session management
     */
    
    /**
     * Check if a user is currently connected
     */
    public boolean isUserConnected(Long userId) {
        Set<String> userSessions = userIdToSessionsMap.get(userId);
        return userSessions != null && !userSessions.isEmpty();
    }
    
    /**
     * Get all active sessions for a user
     */
    public Set<UserSession> getUserSessions(Long userId) {
        Set<String> sessionIds = userIdToSessionsMap.get(userId);
        if (sessionIds == null) {
            return Set.of();
        }
        
        return sessionIds.stream()
                .map(sessionMap::get)
                .filter(session -> session != null && session.isActive())
                .collect(Collectors.toSet());
    }
    
    /**
     * Get all active sessions for a game
     */
    public Set<UserSession> getGameSessions(Long gameId) {
        Set<String> sessionIds = gameIdToSessionsMap.get(gameId);
        if (sessionIds == null) {
            return Set.of();
        }
        
        return sessionIds.stream()
                .map(sessionMap::get)
                .filter(session -> session != null && session.isActive())
                .collect(Collectors.toSet());
    }
    
    /**
     * Send message to a specific user (all their sessions)
     */
    public void sendMessageToUser(Long userId, String messageType, Object message) {
        try {
            Set<UserSession> userSessions = getUserSessions(userId);
            
            if (userSessions.isEmpty()) {
                log.warn("No active sessions found for user: {}", userId);
                return;
            }
            
            WebSocketMessage wsMessage = WebSocketMessage.builder()
                    .type(messageType)
                    .data(message)
                    .messageId(UUID.randomUUID().toString())
                    .build();
            
            for (UserSession session : userSessions) {
                try {
                    messagingTemplate.convertAndSendToUser(
                            session.getUsername(), 
                            "/queue/personal", 
                            wsMessage
                    );
                    session.updateActivity();
                } catch (Exception e) {
                    log.error("Failed to send message to user session: sessionId={}", 
                            session.getSessionId(), e);
                }
            }
            
            log.info("Successfully sent message to user: userId={}, messageType={}, sessionCount={}", 
                    userId, messageType, userSessions.size());
        } catch (Exception e) {
            log.error("Failed to send message to user: userId={}, messageType={}", userId, messageType, e);
        }
    }
    
    /**
     * Broadcast message to all connected users
     */
    public void broadcastToAll(String messageType, Object message) {
        try {
            WebSocketMessage wsMessage = WebSocketMessage.builder()
                    .type(messageType)
                    .data(message)
                    .messageId(UUID.randomUUID().toString())
                    .build();
            
            messagingTemplate.convertAndSend("/topic/broadcast", wsMessage);
            
            log.info("Successfully broadcast message to all users: messageType={}", messageType);
        } catch (Exception e) {
            log.error("Failed to broadcast message to all users: messageType={}", messageType, e);
        }
    }
    
    /**
     * Clean up sessions for a specific game
     */
    private void cleanupGameSessions(Long gameId) {
        Set<String> gameSessions = gameIdToSessionsMap.remove(gameId);
        if (gameSessions != null) {
            gameSessions.forEach(sessionId -> {
                UserSession session = sessionMap.get(sessionId);
                if (session != null) {
                    session.setGameId(null); // Remove game association but keep session
                }
            });
            log.info("Cleaned up {} sessions for game: {}", gameSessions.size(), gameId);
        }
    }
    
    /**
     * Scheduled task to clean up stale sessions
     */
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void cleanupStaleSessions() {
        try {
            Set<String> staleSessionIds = sessionMap.entrySet().stream()
                    .filter(entry -> entry.getValue().isStale(SESSION_TIMEOUT_MINUTES))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            
            staleSessionIds.forEach(this::unregisterUserSession);
            
            if (!staleSessionIds.isEmpty()) {
                log.info("Cleaned up {} stale sessions", staleSessionIds.size());
            }
        } catch (Exception e) {
            log.error("Error during stale session cleanup", e);
        }
    }
    
    /**
     * Get session statistics for monitoring
     */
    public Map<String, Object> getSessionStatistics() {
        return Map.of(
                "totalSessions", sessionMap.size(),
                "uniqueUsers", userIdToSessionsMap.size(),
                "activeGames", gameIdToSessionsMap.size(),
                "timestamp", LocalDateTime.now()
        );
    }
}