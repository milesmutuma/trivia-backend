package com.mabawa.triviacrave.game.service.impl;

import com.mabawa.triviacrave.game.dto.websocket.MessageType;
import com.mabawa.triviacrave.game.entity.Game;
import com.mabawa.triviacrave.game.entity.GamePlayer;
import com.mabawa.triviacrave.game.service.WebSocketMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service that demonstrates integration between game logic and WebSocket messaging
 * This service would be used by GameServiceImpl and LiveGameOrchestrator
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameWebSocketIntegrationService {
    
    private final WebSocketMessageService webSocketMessageService;
    
    /**
     * Handle player joining a game with WebSocket notifications
     */
    public void handlePlayerJoined(Game game, GamePlayer player) {
        try {
            // Send WebSocket notification
            webSocketMessageService.notifyPlayerJoined(game.getId(), player);
            
            // Send player count update
            broadcastPlayerCountUpdate(game);
            
            log.info("Player join notifications sent: gameId={}, playerId={}", 
                    game.getId(), player.getUser().getId());
        } catch (Exception e) {
            log.error("Failed to handle player joined notifications: gameId={}, playerId={}", 
                    game.getId(), player.getUser().getId(), e);
        }
    }
    
    /**
     * Handle player leaving a game with WebSocket notifications
     */
    public void handlePlayerLeft(Game game, GamePlayer player) {
        try {
            // Send WebSocket notification
            webSocketMessageService.notifyPlayerLeft(game.getId(), player);
            
            // Send player count update
            broadcastPlayerCountUpdate(game);
            
            log.info("Player left notifications sent: gameId={}, playerId={}", 
                    game.getId(), player.getUser().getId());
        } catch (Exception e) {
            log.error("Failed to handle player left notifications: gameId={}, playerId={}", 
                    game.getId(), player.getUser().getId(), e);
        }
    }
    
    /**
     * Handle game start with comprehensive notifications
     */
    public void handleGameStart(Game game) {
        try {
            // Send game started notification
            webSocketMessageService.notifyGameStarted(game);
            
            // Send countdown or preparation message
            sendCountdownMessage(game.getId(), 5); // 5 second countdown
            
            log.info("Game start notifications sent: gameId={}", game.getId());
        } catch (Exception e) {
            log.error("Failed to handle game start notifications: gameId={}", game.getId(), e);
        }
    }
    
    /**
     * Handle question distribution with timing
     */
    public void handleQuestionDistribution(Long gameId, Object question, int timeLimit) {
        try {
            // Send question to all players
            webSocketMessageService.sendQuestionToPlayers(gameId, question);
            
            // Send timer update
            broadcastTimerUpdate(gameId, timeLimit);
            
            log.info("Question distributed with timer: gameId={}, timeLimit={}s", gameId, timeLimit);
        } catch (Exception e) {
            log.error("Failed to handle question distribution: gameId={}", gameId, e);
        }
    }
    
    /**
     * Handle answer results and score updates
     */
    public void handleAnswerResults(Long gameId, Map<String, Object> results) {
        try {
            // Send score updates
            webSocketMessageService.notifyScoreUpdate(gameId, results);
            
            // Send leaderboard update
            if (results.containsKey("leaderboard")) {
                webSocketMessageService.broadcastLeaderboard(gameId, results.get("leaderboard"));
            }
            
            log.info("Answer results broadcasted: gameId={}", gameId);
        } catch (Exception e) {
            log.error("Failed to handle answer results: gameId={}", gameId, e);
        }
    }
    
    /**
     * Handle game completion with final results
     */
    public void handleGameCompletion(Game game, Map<String, Object> finalResults) {
        try {
            // Send game ended notification with results
            webSocketMessageService.notifyGameEnded(game, finalResults);
            
            // Send final leaderboard
            if (finalResults.containsKey("finalLeaderboard")) {
                webSocketMessageService.broadcastToGame(
                        game.getId(), 
                        MessageType.FINAL_RESULTS.getValue(), 
                        finalResults.get("finalLeaderboard")
                );
            }
            
            log.info("Game completion notifications sent: gameId={}", game.getId());
        } catch (Exception e) {
            log.error("Failed to handle game completion notifications: gameId={}", game.getId(), e);
        }
    }
    
    /**
     * Handle game state changes (pause, resume, etc.)
     */
    public void handleGameStateChange(Game game, Game.GameStatus previousStatus) {
        try {
            webSocketMessageService.notifyGameStateChange(game.getId(), game.getStatus());
            
            // Send additional context based on state change
            String message = getStateChangeMessage(previousStatus, game.getStatus());
            if (message != null) {
                broadcastSystemMessage(game.getId(), message);
            }
            
            log.info("Game state change notifications sent: gameId={}, {} -> {}", 
                    game.getId(), previousStatus, game.getStatus());
        } catch (Exception e) {
            log.error("Failed to handle game state change notifications: gameId={}", game.getId(), e);
        }
    }
    
    /**
     * Handle real-time events during gameplay
     */
    public void handleRealtimeEvent(Long gameId, String eventType, Object eventData) {
        try {
            webSocketMessageService.broadcastToGame(gameId, eventType, eventData);
            log.info("Real-time event broadcasted: gameId={}, eventType={}", gameId, eventType);
        } catch (Exception e) {
            log.error("Failed to handle real-time event: gameId={}, eventType={}", gameId, eventType, e);
        }
    }
    
    // Private helper methods
    
    private void broadcastPlayerCountUpdate(Game game) {
        Map<String, Object> playerCountData = Map.of(
                "gameId", game.getId(),
                "currentPlayers", game.getCurrentPlayers(),
                "maxPlayers", game.getMaxPlayers(),
                "timestamp", LocalDateTime.now()
        );
        
        webSocketMessageService.broadcastToGame(
                game.getId(), 
                "player.count.update", 
                playerCountData
        );
    }
    
    private void sendCountdownMessage(Long gameId, int seconds) {
        for (int i = seconds; i > 0; i--) {
            Map<String, Object> countdownData = Map.of(
                    "type", "countdown",
                    "secondsRemaining", i,
                    "message", "Game starts in " + i + " second" + (i > 1 ? "s" : "")
            );
            
            webSocketMessageService.broadcastToGame(gameId, "game.countdown", countdownData);
            
            try {
                Thread.sleep(1000); // Wait 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Send start message
        Map<String, Object> startData = Map.of(
                "type", "start",
                "message", "Game started! Good luck!"
        );
        webSocketMessageService.broadcastToGame(gameId, "game.start", startData);
    }
    
    private void broadcastTimerUpdate(Long gameId, int timeLimit) {
        Map<String, Object> timerData = Map.of(
                "type", "timer",
                "timeLimit", timeLimit,
                "startTime", LocalDateTime.now(),
                "message", "You have " + timeLimit + " seconds to answer"
        );
        
        webSocketMessageService.broadcastToGame(gameId, "question.timer", timerData);
    }
    
    private String getStateChangeMessage(Game.GameStatus previousStatus, Game.GameStatus newStatus) {
        return switch (newStatus) {
            case IN_PROGRESS -> "Game has started!";
            case PAUSED -> "Game has been paused";
            case COMPLETED -> "Game has ended";
            case ABANDONED -> "Game has been abandoned";
            default -> null;
        };
    }
    
    private void broadcastSystemMessage(Long gameId, String message) {
        Map<String, Object> systemMessage = Map.of(
                "type", "system",
                "message", message,
                "timestamp", LocalDateTime.now()
        );
        
        webSocketMessageService.broadcastToGame(gameId, MessageType.SYSTEM_MESSAGE.getValue(), systemMessage);
    }
}