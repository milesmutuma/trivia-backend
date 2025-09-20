package com.mabawa.triviacrave.game.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mabawa.triviacrave.generated.graphql.types.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service responsible for publishing game events to Redis Pub/Sub channels
 * for cross-server communication and WebSocket broadcasting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameEventPublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // Redis channel prefixes for different event types
    private static final String GAME_CHANNEL_PREFIX = "game:events:";
    private static final String LOBBY_CHANNEL_PREFIX = "lobby:events:";
    private static final String LEADERBOARD_CHANNEL_PREFIX = "leaderboard:events:";
    private static final String TIMER_CHANNEL_PREFIX = "timer:events:";
    private static final String GLOBAL_GAME_CHANNEL = "game:events:global";
    
    /**
     * Publish game state update to Redis channel
     */
    public void publishGameStateUpdate(GameStateUpdate update) {
        try {
            String channel = GAME_CHANNEL_PREFIX + update.getGameId();
            GameEventMessage message = createEventMessage("GAME_STATE_UPDATE", update);
            
            publish(channel, message);
            
            // Also publish to global channel for monitoring
            publish(GLOBAL_GAME_CHANNEL, message);
            
            log.debug("Published game state update: gameId={}, eventType={}", 
                     update.getGameId(), update.getEventType());
                     
        } catch (Exception e) {
            log.error("Error publishing game state update: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish lobby update to Redis channel
     */
    public void publishLobbyUpdate(LobbyUpdate update) {
        try {
            String channel = LOBBY_CHANNEL_PREFIX + update.getGameId();
            GameEventMessage message = createEventMessage("LOBBY_UPDATE", update);
            
            publish(channel, message);
            
            log.debug("Published lobby update: gameId={}, playerCount={}, eventType={}", 
                     update.getGameId(), update.getPlayerCount(), update.getEventType());
                     
        } catch (Exception e) {
            log.error("Error publishing lobby update: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish leaderboard update to Redis channel
     */
    public void publishLeaderboardUpdate(LeaderboardUpdate update) {
        try {
            String channel = LEADERBOARD_CHANNEL_PREFIX + update.getGameId();
            GameEventMessage message = createEventMessage("LEADERBOARD_UPDATE", update);
            
            publish(channel, message);
            
            log.debug("Published leaderboard update: gameId={}, updatedPlayer={}", 
                     update.getGameId(), update.getUpdatedPlayerId());
                     
        } catch (Exception e) {
            log.error("Error publishing leaderboard update: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish timer update to Redis channel
     */
    public void publishTimerUpdate(TimerUpdate update) {
        try {
            String channel = TIMER_CHANNEL_PREFIX + update.getGameId();
            GameEventMessage message = createEventMessage("TIMER_UPDATE", update);
            
            publish(channel, message);
            
            log.debug("Published timer update: gameId={}, remaining={}, warning={}", 
                     update.getGameId(), update.getRemainingSeconds(), update.getIsWarning());
                     
        } catch (Exception e) {
            log.error("Error publishing timer update: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish answer result to Redis channel
     */
    public void publishAnswerResult(AnswerResult result) {
        try {
            String channel = GAME_CHANNEL_PREFIX + result.getGameId();
            GameEventMessage message = createEventMessage("ANSWER_RESULT", result);
            
            publish(channel, message);
            
            log.debug("Published answer result: gameId={}, userId={}, correct={}, points={}", 
                     result.getGameId(), result.getUserId(), result.getIsCorrect(), result.getPointsEarned());
                     
        } catch (Exception e) {
            log.error("Error publishing answer result: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish player timeout to Redis channel
     */
    public void publishPlayerTimeout(PlayerTimeout timeout) {
        try {
            String channel = GAME_CHANNEL_PREFIX + timeout.getGameId();
            GameEventMessage message = createEventMessage("PLAYER_TIMEOUT", timeout);
            
            publish(channel, message);
            
            log.debug("Published player timeout: gameId={}, userId={}, questionIndex={}", 
                     timeout.getGameId(), timeout.getUserId(), timeout.getQuestionIndex());
                     
        } catch (Exception e) {
            log.error("Error publishing player timeout: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish player joined event
     */
    public void publishPlayerJoined(Long gameId, Long userId, String username) {
        try {
            LobbyUpdate lobbyUpdate = LobbyUpdate.newBuilder()
                    .gameId(gameId)
                    .playerId(userId)
                    .playerUsername(username)
                    .eventType(GameEventType.PLAYER_JOINED)
                    .timestamp(LocalDateTime.now())
                    .build();
                    
            publishLobbyUpdate(lobbyUpdate);
            
        } catch (Exception e) {
            log.error("Error publishing player joined event: gameId={}, userId={}, error={}", 
                     gameId, userId, e.getMessage(), e);
        }
    }

    /**
     * Publish player left event
     */
    public void publishPlayerLeft(Long gameId, Long userId, String username) {
        try {
            LobbyUpdate lobbyUpdate = LobbyUpdate.newBuilder()
                    .gameId(gameId)
                    .playerId(userId)
                    .playerUsername(username)
                    .eventType(GameEventType.PLAYER_LEFT)
                    .timestamp(LocalDateTime.now())
                    .build();
                    
            publishLobbyUpdate(lobbyUpdate);
            
        } catch (Exception e) {
            log.error("Error publishing player left event: gameId={}, userId={}, error={}", 
                     gameId, userId, e.getMessage(), e);
        }
    }

    /**
     * Publish player ready status change
     */
    public void publishPlayerReadyChanged(Long gameId, Long userId, String username, boolean isReady) {
        try {
            LobbyUpdate lobbyUpdate = LobbyUpdate.newBuilder()
                    .gameId(gameId)
                    .playerId(userId)
                    .playerUsername(username)
                    .eventType(GameEventType.PLAYER_READY_CHANGED)
                    .timestamp(LocalDateTime.now())
                    .build();
                    
            publishLobbyUpdate(lobbyUpdate);
            
        } catch (Exception e) {
            log.error("Error publishing player ready changed event: gameId={}, userId={}, ready={}, error={}", 
                     gameId, userId, isReady, e.getMessage(), e);
        }
    }

    /**
     * Publish custom game message
     */
    public void publishCustomGameMessage(Long gameId, String message, GameEventType eventType) {
        try {
            GameStateUpdate customUpdate = GameStateUpdate.newBuilder()
                    .gameId(gameId)
                    .status(GameStatus.ACTIVE) // Default status
                    .eventType(eventType)
                    .message(message)
                    .timestamp(LocalDateTime.now())
                    .build();
                    
            publishGameStateUpdate(customUpdate);
            
        } catch (Exception e) {
            log.error("Error publishing custom game message: gameId={}, message={}, error={}", 
                     gameId, message, e.getMessage(), e);
        }
    }

    /**
     * Publish game error
     */
    public void publishGameError(Long gameId, String errorCode, String errorMessage) {
        try {
            String channel = GAME_CHANNEL_PREFIX + gameId;
            
            SubscriptionError error = SubscriptionError.newBuilder()
                    .code(errorCode)
                    .message(errorMessage)
                    .gameId(gameId)
                    .timestamp(LocalDateTime.now())
                    .build();
                    
            GameEventMessage message = createEventMessage("SUBSCRIPTION_ERROR", error);
            publish(channel, message);
            
            log.warn("Published game error: gameId={}, code={}, message={}", gameId, errorCode, errorMessage);
            
        } catch (Exception e) {
            log.error("Error publishing game error: gameId={}, error={}", gameId, e.getMessage(), e);
        }
    }

    /**
     * Publish connection status update
     */
    public void publishConnectionStatus(Long gameId, Long userId, boolean isConnected) {
        try {
            String channel = GAME_CHANNEL_PREFIX + gameId;
            
            ConnectionStatus status = ConnectionStatus.newBuilder()
                    .isConnected(isConnected)
                    .gameId(gameId)
                    .userId(userId)
                    .connectionTime(isConnected ? LocalDateTime.now() : null)
                    .lastPing(LocalDateTime.now())
                    .build();
                    
            GameEventMessage message = createEventMessage("CONNECTION_STATUS", status);
            publish(channel, message);
            
            log.debug("Published connection status: gameId={}, userId={}, connected={}", 
                     gameId, userId, isConnected);
                     
        } catch (Exception e) {
            log.error("Error publishing connection status: gameId={}, userId={}, error={}", 
                     gameId, userId, e.getMessage(), e);
        }
    }

    /**
     * Publish subscription heartbeat for monitoring
     */
    public void publishSubscriptionHeartbeat(Long gameId, int subscriberCount) {
        try {
            String channel = GAME_CHANNEL_PREFIX + gameId + ":heartbeat";
            
            Map<String, Object> heartbeat = Map.of(
                    "gameId", gameId,
                    "subscriberCount", subscriberCount,
                    "timestamp", LocalDateTime.now(),
                    "serverInstance", getServerInstanceId()
            );
            
            publish(channel, heartbeat);
            
            log.trace("Published subscription heartbeat: gameId={}, subscribers={}", gameId, subscriberCount);
            
        } catch (Exception e) {
            log.error("Error publishing subscription heartbeat: gameId={}, error={}", gameId, e.getMessage(), e);
        }
    }

    // Private helper methods

    private void publish(String channel, Object message) {
        try {
            redisTemplate.convertAndSend(channel, message);
            log.trace("Published to channel '{}': {}", channel, 
                     message instanceof GameEventMessage ? ((GameEventMessage) message).getEventType() : "heartbeat");
                     
        } catch (Exception e) {
            log.error("Error publishing to Redis channel '{}': {}", channel, e.getMessage(), e);
            throw new RuntimeException("Failed to publish message to Redis", e);
        }
    }

    private GameEventMessage createEventMessage(String eventType, Object data) {
        try {
            return GameEventMessage.builder()
                    .eventType(eventType)
                    .data(objectMapper.writeValueAsString(data))
                    .timestamp(LocalDateTime.now())
                    .serverId(getServerInstanceId())
                    .build();
                    
        } catch (JsonProcessingException e) {
            log.error("Error serializing event data: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to serialize event data", e);
        }
    }

    private String getServerInstanceId() {
        // Get server instance ID for multi-server setups
        // This could be from environment variable, hostname, or generated UUID
        return System.getProperty("server.instance.id", "default-server");
    }

    /**
     * Message wrapper for Redis Pub/Sub events
     */
    @lombok.Builder
    @lombok.Data
    public static class GameEventMessage {
        private String eventType;
        private String data;
        private LocalDateTime timestamp;
        private String serverId;
    }
}