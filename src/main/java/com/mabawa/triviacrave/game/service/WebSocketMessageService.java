package com.mabawa.triviacrave.game.service;

import com.mabawa.triviacrave.game.entity.Game;
import com.mabawa.triviacrave.game.entity.GamePlayer;

/**
 * Service interface for WebSocket messaging in real-time trivia games
 */
public interface WebSocketMessageService {
    
    /**
     * Notify all players in a game about a player joining
     */
    void notifyPlayerJoined(Long gameId, GamePlayer player);
    
    /**
     * Notify all players in a game about a player leaving
     */
    void notifyPlayerLeft(Long gameId, GamePlayer player);
    
    /**
     * Notify all players when a game starts
     */
    void notifyGameStarted(Game game);
    
    /**
     * Send the next question to all players in a game
     */
    void sendQuestionToPlayers(Long gameId, Object question);
    
    /**
     * Notify all players about score updates after a question
     */
    void notifyScoreUpdate(Long gameId, Object scoreUpdate);
    
    /**
     * Notify all players when game ends with final results
     */
    void notifyGameEnded(Game game, Object results);
    
    /**
     * Send a private message to a specific player
     */
    void sendPrivateMessage(String username, String destination, Object message);
    
    /**
     * Notify players about game state changes (pause, resume, etc.)
     */
    void notifyGameStateChange(Long gameId, Game.GameStatus status);
    
    /**
     * Send player readiness updates to all players in lobby
     */
    void notifyPlayerReadiness(Long gameId, GamePlayer player);
    
    /**
     * Broadcast leaderboard updates during game
     */
    void broadcastLeaderboard(Long gameId, Object leaderboard);
    
    /**
     * Send connection status notifications
     */
    void notifyConnectionStatus(String username, boolean connected);
    
    /**
     * Broadcast a generic message to all players in a game
     */
    void broadcastToGame(Long gameId, String messageType, Object message);
    
    /**
     * Register a user session for WebSocket communication
     */
    void registerUserSession(String sessionKey, Long userId, Long gameId);
    
    /**
     * Unregister a user session
     */
    void unregisterUserSession(String sessionKey);
}