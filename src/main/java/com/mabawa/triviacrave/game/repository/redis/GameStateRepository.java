package com.mabawa.triviacrave.game.repository.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
public class GameStateRepository {
    
    private final RedisTemplate<String, String> stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    
    private static final String GAME_STATE_PREFIX = "gamestate:";
    private static final String GAME_LOBBY_PREFIX = "gamelobby:";
    private static final String PLAYER_READY_PREFIX = "playerready:";
    private static final String GAME_TIMER_PREFIX = "gametimer:";
    private static final Duration DEFAULT_GAME_TTL = Duration.ofHours(2);
    
    /**
     * Store active game state with TTL
     */
    public void saveGameState(Long gameId, GameState gameState) {
        try {
            String key = getGameStateKey(gameId);
            String jsonValue = objectMapper.writeValueAsString(gameState);
            stringRedisTemplate.opsForValue().set(key, jsonValue, DEFAULT_GAME_TTL);
            log.debug("Saved game state for game {}", gameId);
        } catch (JsonProcessingException e) {
            log.error("Error serializing game state for game {}: {}", gameId, e.getMessage(), e);
        }
    }
    
    /**
     * Retrieve active game state
     */
    public GameState getGameState(Long gameId) {
        try {
            String key = getGameStateKey(gameId);
            String jsonValue = stringRedisTemplate.opsForValue().get(key);
            
            if (jsonValue == null) {
                return null;
            }
            
            return objectMapper.readValue(jsonValue, GameState.class);
        } catch (Exception e) {
            log.error("Error deserializing game state for game {}: {}", gameId, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Remove game state (when game ends)
     */
    public void deleteGameState(Long gameId) {
        String key = getGameStateKey(gameId);
        stringRedisTemplate.delete(key);
        log.debug("Deleted game state for game {}", gameId);
    }
    
    /**
     * Check if game state exists
     */
    public boolean gameStateExists(Long gameId) {
        String key = getGameStateKey(gameId);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }
    
    /**
     * Add player to game lobby
     */
    public void addPlayerToLobby(Long gameId, String userId) {
        String key = getGameLobbyKey(gameId);
        stringRedisTemplate.opsForSet().add(key, userId);
        stringRedisTemplate.expire(key, DEFAULT_GAME_TTL);
        log.debug("Added player {} to lobby for game {}", userId, gameId);
    }
    
    /**
     * Remove player from game lobby
     */
    public void removePlayerFromLobby(Long gameId, String userId) {
        String key = getGameLobbyKey(gameId);
        stringRedisTemplate.opsForSet().remove(key, userId);
        log.debug("Removed player {} from lobby for game {}", userId, gameId);
    }
    
    /**
     * Get all players in game lobby
     */
    public Set<String> getPlayersInLobby(Long gameId) {
        String key = getGameLobbyKey(gameId);
        return stringRedisTemplate.opsForSet().members(key);
    }
    
    /**
     * Get player count in lobby
     */
    public Long getPlayerCountInLobby(Long gameId) {
        String key = getGameLobbyKey(gameId);
        return stringRedisTemplate.opsForSet().size(key);
    }
    
    /**
     * Set player ready status
     */
    public void setPlayerReady(Long gameId, String userId, boolean ready) {
        String key = getPlayerReadyKey(gameId);
        if (ready) {
            stringRedisTemplate.opsForSet().add(key, userId);
            log.debug("Set player {} as ready for game {}", userId, gameId);
        } else {
            stringRedisTemplate.opsForSet().remove(key, userId);
            log.debug("Set player {} as not ready for game {}", userId, gameId);
        }
        stringRedisTemplate.expire(key, DEFAULT_GAME_TTL);
    }
    
    /**
     * Check if player is ready
     */
    public boolean isPlayerReady(Long gameId, String userId) {
        String key = getPlayerReadyKey(gameId);
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, userId));
    }
    
    /**
     * Get all ready players
     */
    public Set<String> getReadyPlayers(Long gameId) {
        String key = getPlayerReadyKey(gameId);
        return stringRedisTemplate.opsForSet().members(key);
    }
    
    /**
     * Get ready player count
     */
    public Long getReadyPlayerCount(Long gameId) {
        String key = getPlayerReadyKey(gameId);
        return stringRedisTemplate.opsForSet().size(key);
    }
    
    /**
     * Set question timer (in seconds)
     */
    public void setQuestionTimer(Long gameId, int seconds) {
        String key = getGameTimerKey(gameId);
        stringRedisTemplate.opsForValue().set(key, String.valueOf(seconds), Duration.ofSeconds(seconds + 10));
        log.debug("Set question timer for game {} to {} seconds", gameId, seconds);
    }
    
    /**
     * Get remaining question time
     */
    public Integer getQuestionTimer(Long gameId) {
        String key = getGameTimerKey(gameId);
        String value = stringRedisTemplate.opsForValue().get(key);
        
        if (value == null) {
            return null;
        }
        
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid timer value for game {}: {}", gameId, value);
            return null;
        }
    }
    
    /**
     * Decrement question timer and return remaining time
     */
    public Long decrementQuestionTimer(Long gameId) {
        String key = getGameTimerKey(gameId);
        return stringRedisTemplate.opsForValue().decrement(key);
    }
    
    /**
     * Clear all game-related data
     */
    public void clearAllGameData(Long gameId) {
        deleteGameState(gameId);
        stringRedisTemplate.delete(getGameLobbyKey(gameId));
        stringRedisTemplate.delete(getPlayerReadyKey(gameId));
        stringRedisTemplate.delete(getGameTimerKey(gameId));
        log.debug("Cleared all Redis data for game {}", gameId);
    }
    
    // Private helper methods for Redis key generation
    
    private String getGameStateKey(Long gameId) {
        return GAME_STATE_PREFIX + gameId;
    }
    
    private String getGameLobbyKey(Long gameId) {
        return GAME_LOBBY_PREFIX + gameId;
    }
    
    private String getPlayerReadyKey(Long gameId) {
        return PLAYER_READY_PREFIX + gameId;
    }
    
    private String getGameTimerKey(Long gameId) {
        return GAME_TIMER_PREFIX + gameId;
    }
    
    /**
     * Game state data structure for Redis storage
     */
    public static class GameState {
        private Long gameId;
        private String status; // WAITING, IN_PROGRESS, COMPLETED, ABANDONED
        private Integer currentQuestionIndex;
        private Integer totalQuestions;
        private Long currentQuestionId;
        private java.time.LocalDateTime questionStartTime;
        private java.time.LocalDateTime gameStartTime;
        private java.time.LocalDateTime lastActivityTime;
        private java.util.Map<String, Integer> playerScores; // userId -> score
        
        // Default constructor for Jackson
        public GameState() {
            this.playerScores = new java.util.HashMap<>();
            this.lastActivityTime = java.time.LocalDateTime.now();
        }
        
        // Getters and setters
        public Long getGameId() { return gameId; }
        public void setGameId(Long gameId) { this.gameId = gameId; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public Integer getCurrentQuestionIndex() { return currentQuestionIndex; }
        public void setCurrentQuestionIndex(Integer currentQuestionIndex) { this.currentQuestionIndex = currentQuestionIndex; }
        
        public Integer getTotalQuestions() { return totalQuestions; }
        public void setTotalQuestions(Integer totalQuestions) { this.totalQuestions = totalQuestions; }
        
        public Long getCurrentQuestionId() { return currentQuestionId; }
        public void setCurrentQuestionId(Long currentQuestionId) { this.currentQuestionId = currentQuestionId; }
        
        public java.time.LocalDateTime getQuestionStartTime() { return questionStartTime; }
        public void setQuestionStartTime(java.time.LocalDateTime questionStartTime) { this.questionStartTime = questionStartTime; }
        
        public java.time.LocalDateTime getGameStartTime() { return gameStartTime; }
        public void setGameStartTime(java.time.LocalDateTime gameStartTime) { this.gameStartTime = gameStartTime; }
        
        public java.time.LocalDateTime getLastActivityTime() { return lastActivityTime; }
        public void setLastActivityTime(java.time.LocalDateTime lastActivityTime) { this.lastActivityTime = lastActivityTime; }
        
        public java.util.Map<String, Integer> getPlayerScores() { return playerScores; }
        public void setPlayerScores(java.util.Map<String, Integer> playerScores) { this.playerScores = playerScores; }
        
        // Utility methods
        public void updateActivity() {
            this.lastActivityTime = java.time.LocalDateTime.now();
        }
        
        public void addPlayerScore(String userId, int score) {
            this.playerScores.put(userId, this.playerScores.getOrDefault(userId, 0) + score);
            updateActivity();
        }
        
        public Integer getPlayerScore(String userId) {
            return this.playerScores.getOrDefault(userId, 0);
        }
    }
}