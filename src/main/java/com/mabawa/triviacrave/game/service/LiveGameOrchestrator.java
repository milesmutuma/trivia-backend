package com.mabawa.triviacrave.game.service;

import com.mabawa.triviacrave.game.entity.Game;
import com.mabawa.triviacrave.game.entity.GamePlayer;
import com.mabawa.triviacrave.game.entity.GameQuestion;
import com.mabawa.triviacrave.game.repository.GamePlayerRepository;
import com.mabawa.triviacrave.game.repository.GameRepository;
import com.mabawa.triviacrave.game.service.redis.RedisLeaderboardService;
import com.mabawa.triviacrave.generated.graphql.types.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central orchestrator for managing live game sessions with real-time updates.
 * Handles game timers, automatic question progression, live score updates,
 * and real-time event broadcasting for live gameplay.
 *
 * EVENT BROADCASTING ARCHITECTURE:
 * This service has been consolidated to use GraphQL subscriptions as the single
 * source of truth for all game events. All real-time updates are broadcast
 * exclusively through GameEventPublisher, which feeds the GraphQL subscription
 * system via Redis pub/sub. This eliminates duplicate event broadcasting that
 * previously occurred through both GraphQL subscriptions and direct WebSocket
 * messaging.
 *
 * Key benefits of this consolidation:
 * - Single event source eliminates duplicate messages
 * - Consistent event format across all clients
 * - Simplified debugging and monitoring
 * - Better scalability through Redis pub/sub
 * - Centralized event handling logic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveGameOrchestrator {

    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisLeaderboardService leaderboardService;
    private final GameEventPublisher gameEventPublisher;
    
    // In-memory cache for active game timers
    private final Map<Long, GameTimer> activeTimers = new ConcurrentHashMap<>();
    
    private static final String GAME_TIMER_PREFIX = "game:timer:";
    private static final String GAME_STATE_PREFIX = "game:state:";
    private static final int DEFAULT_QUESTION_TIME_SECONDS = 30;
    private static final int COUNTDOWN_WARNING_SECONDS = 10;

    /**
     * Start a live game session with timer management
     */
    public void startLiveGame(Long gameId) {
        try {
            Game game = gameRepository.findOne(gameId);
            if (game == null) {
                log.warn("Cannot start live game - game not found: {}", gameId);
                return;
            }

            if (!game.isGameActive()) {
                log.warn("Cannot start live game - game not active: {}", gameId);
                return;
            }

            // Initialize game timer
            initializeGameTimer(game);
            
            // Cache game state in Redis
            cacheGameState(game);
            
            // Broadcast game start event
            GameStateUpdate gameStateUpdate = GameStateUpdate.newBuilder()
                    .gameId(gameId)
                    .status(GameStatus.ACTIVE)
                    .currentQuestionIndex(0)
                    .totalQuestions(game.getGameQuestions().size())
                    .timestamp(LocalDateTime.now())
                    .eventType(GameEventType.GAME_STARTED)
                    .build();
                    
            broadcastGameStateUpdate(gameStateUpdate);
            
            // Start first question timer
            startQuestionTimer(game, 0);
            
            // First question will be distributed via GraphQL subscriptions through GameEventPublisher
            // This eliminates duplicate WebSocket messaging while maintaining real-time updates
            log.debug("First question will be distributed via GraphQL subscriptions: gameId={}", gameId);
            
            log.info("Live game started successfully: gameId={}", gameId);
            
        } catch (Exception e) {
            log.error("Error starting live game {}: {}", gameId, e.getMessage(), e);
        }
    }

    /**
     * Handle answer submission with real-time score updates
     */
    public void handleAnswerSubmission(Long gameId, Long userId, String selectedAnswer, 
                                     Integer timeSpent, Long questionId) {
        try {
            Game game = gameRepository.findOne(gameId);
            if (game == null) {
                log.warn("Game not found for answer submission: {}", gameId);
                return;
            }

            // Find the current question
            GameQuestion currentQuestion = game.getGameQuestions().stream()
                    .filter(gq -> gq.getQuestion().getId().equals(questionId))
                    .findFirst()
                    .orElse(null);

            if (currentQuestion == null) {
                log.warn("Question not found in game: gameId={}, questionId={}", gameId, questionId);
                return;
            }

            // Check if answer is correct
            boolean isCorrect = selectedAnswer.equals(currentQuestion.getCorrectAnswer());
            
            // Calculate score (this would integrate with existing ScoreService)
            int pointsEarned = calculateQuestionScore(currentQuestion, timeSpent, isCorrect);
            
            // Update live leaderboard
            updateLiveLeaderboard(gameId, userId, pointsEarned);
            
            // Broadcast answer result to all players
            AnswerResult answerResult = AnswerResult.newBuilder()
                    .gameId(gameId)
                    .userId(userId)
                    .questionId(questionId)
                    .selectedAnswer(selectedAnswer)
                    .correctAnswer(currentQuestion.getCorrectAnswer())
                    .isCorrect(isCorrect)
                    .pointsEarned(pointsEarned)
                    .timeSpent(timeSpent)
                    .timestamp(LocalDateTime.now())
                    .build();
                    
            broadcastAnswerResult(answerResult);
            
            // Check if all players have answered
            if (allPlayersAnswered(gameId, questionId)) {
                completeCurrentQuestion(gameId);
            }
            
        } catch (Exception e) {
            log.error("Error handling answer submission: gameId={}, userId={}, error={}", 
                     gameId, userId, e.getMessage(), e);
        }
    }

    /**
     * Force progression to next question when timer expires
     */
    public void forceQuestionProgression(Long gameId, Integer questionIndex) {
        try {
            Game game = gameRepository.findOne(gameId);
            if (game == null || !game.isGameActive()) {
                return;
            }

            log.info("Forcing question progression: gameId={}, questionIndex={}", gameId, questionIndex);
            
            // Mark unanswered questions as timed out
            markUnansweredQuestionsTimedOut(gameId, questionIndex);
            
            // Progress to next question or end game
            progressToNextQuestion(game, questionIndex);
            
        } catch (Exception e) {
            log.error("Error forcing question progression: gameId={}, questionIndex={}, error={}", 
                     gameId, questionIndex, e.getMessage(), e);
        }
    }

    /**
     * End a live game session with enhanced completion data handling
     */
    public void endLiveGame(Long gameId, GameEndReason reason) {
        endLiveGame(gameId, reason, null);
    }

    /**
     * End a live game session with completion data
     */
    public void endLiveGame(Long gameId, GameEndReason reason, Map<String, Object> completionData) {
        try {
            // Stop and remove timer
            GameTimer timer = activeTimers.remove(gameId);
            if (timer != null) {
                timer.stop();
            }
            
            // Remove timer from Redis
            redisTemplate.delete(GAME_TIMER_PREFIX + gameId);
            
            // Calculate final scores and rankings
            List<PlayerScore> finalScores = calculateFinalScores(gameId);
            
            // Update persistent leaderboards
            updatePersistentLeaderboards(gameId, finalScores);
            
            // Game completion will be broadcast via GraphQL subscriptions through GameEventPublisher
            // This eliminates duplicate messaging while maintaining comprehensive completion data
            log.debug("Game completion will be broadcast via GraphQL subscriptions: gameId={}", gameId);
            
            // Broadcast game end event
            GameStateUpdate gameEndUpdate = GameStateUpdate.newBuilder()
                    .gameId(gameId)
                    .status(GameStatus.COMPLETED)
                    .timestamp(LocalDateTime.now())
                    .eventType(GameEventType.GAME_ENDED)
                    .endReason(reason)
                    .finalScores(finalScores)
                    .build();
                    
            broadcastGameStateUpdate(gameEndUpdate);
            
            // Clean up Redis cache
            cleanupGameRedisData(gameId);
            
            log.info("Live game ended: gameId={}, reason={}, hasCompletionData={}", 
                    gameId, reason, completionData != null && !completionData.isEmpty());
            
        } catch (Exception e) {
            log.error("Error ending live game {}: {}", gameId, e.getMessage(), e);
        }
    }

    /**
     * Get current game timer state
     */
    public TimerUpdate getCurrentTimerState(Long gameId) {
        try {
            String timerKey = GAME_TIMER_PREFIX + gameId;
            Object timerData = redisTemplate.opsForValue().get(timerKey);
            
            if (timerData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> timer = (Map<String, Object>) timerData;
                
                Integer questionIndex = (Integer) timer.get("questionIndex");
                Long endTime = (Long) timer.get("endTime");
                Integer duration = (Integer) timer.get("duration");
                
                if (endTime != null) {
                    long currentTime = System.currentTimeMillis();
                    int remainingSeconds = Math.max(0, (int) ((endTime - currentTime) / 1000));
                    
                    return TimerUpdate.newBuilder()
                            .gameId(gameId)
                            .questionIndex(questionIndex != null ? questionIndex : 0)
                            .remainingSeconds(remainingSeconds)
                            .totalSeconds(duration != null ? duration : DEFAULT_QUESTION_TIME_SECONDS)
                            .isWarning(remainingSeconds <= COUNTDOWN_WARNING_SECONDS)
                            .timestamp(LocalDateTime.now())
                            .build();
                }
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Error getting timer state for game {}: {}", gameId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Scheduled task to process expired timers
     */
    @Scheduled(fixedDelay = 1000) // Run every second
    public void processExpiredTimers() {
        try {
            long currentTime = System.currentTimeMillis();
            
            activeTimers.entrySet().removeIf(entry -> {
                Long gameId = entry.getKey();
                GameTimer timer = entry.getValue();
                
                if (timer.isExpired(currentTime)) {
                    log.debug("Timer expired for game: {}", gameId);
                    
                    // Handle timer expiration asynchronously
                    handleTimerExpiration(gameId, timer.getQuestionIndex());
                    return true;
                }
                
                // Send timer updates for countdown warnings
                if (timer.shouldSendUpdate(currentTime)) {
                    sendTimerUpdate(gameId, timer, currentTime);
                    timer.markUpdateSent();
                }
                
                return false;
            });
            
        } catch (Exception e) {
            log.error("Error processing expired timers: {}", e.getMessage(), e);
        }
    }

    // Private helper methods

    private void initializeGameTimer(Game game) {
        GameTimer timer = new GameTimer(game.getId(), 0, DEFAULT_QUESTION_TIME_SECONDS);
        activeTimers.put(game.getId(), timer);
    }

    private void startQuestionTimer(Game game, Integer questionIndex) {
        try {
            GameTimer timer = new GameTimer(game.getId(), questionIndex, DEFAULT_QUESTION_TIME_SECONDS);
            activeTimers.put(game.getId(), timer);
            
            // Store timer in Redis for persistence across server restarts
            String timerKey = GAME_TIMER_PREFIX + game.getId();
            Map<String, Object> timerData = Map.of(
                    "gameId", game.getId(),
                    "questionIndex", questionIndex,
                    "startTime", System.currentTimeMillis(),
                    "endTime", System.currentTimeMillis() + (DEFAULT_QUESTION_TIME_SECONDS * 1000L),
                    "duration", DEFAULT_QUESTION_TIME_SECONDS
            );
            
            redisTemplate.opsForValue().set(timerKey, timerData, Duration.ofSeconds(DEFAULT_QUESTION_TIME_SECONDS + 10));
            
            // Broadcast timer start
            TimerUpdate timerUpdate = TimerUpdate.newBuilder()
                    .gameId(game.getId())
                    .questionIndex(questionIndex)
                    .remainingSeconds(DEFAULT_QUESTION_TIME_SECONDS)
                    .totalSeconds(DEFAULT_QUESTION_TIME_SECONDS)
                    .isWarning(false)
                    .timestamp(LocalDateTime.now())
                    .build();
                    
            broadcastTimerUpdate(timerUpdate);
            
        } catch (Exception e) {
            log.error("Error starting question timer: gameId={}, questionIndex={}, error={}", 
                     game.getId(), questionIndex, e.getMessage(), e);
        }
    }

    private void cacheGameState(Game game) {
        try {
            String stateKey = GAME_STATE_PREFIX + game.getId();
            GameStateCache gameState = GameStateCache.builder()
                    .gameId(game.getId())
                    .status(game.getStatus())
                    .currentQuestionIndex(game.getQuestionsAnswered())
                    .totalQuestions(game.getGameQuestions().size())
                    .playerCount(game.getCurrentPlayers())
                    .lastUpdated(LocalDateTime.now())
                    .build();
                    
            redisTemplate.opsForValue().set(stateKey, gameState, Duration.ofHours(2));
            
        } catch (Exception e) {
            log.error("Error caching game state for game {}: {}", game.getId(), e.getMessage(), e);
        }
    }

    private int calculateQuestionScore(GameQuestion question, Integer timeSpent, boolean isCorrect) {
        if (!isCorrect) {
            return 0;
        }
        
        // Base score calculation (this should integrate with existing ScoreService)
        int baseScore = 100; // Base points for correct answer
        
        // Time bonus (faster answers get more points)
        int timeBonus = Math.max(0, DEFAULT_QUESTION_TIME_SECONDS - timeSpent) * 2;
        
        // Difficulty bonus (could be extracted from question difficulty)
        int difficultyBonus = 0; // This would come from question difficulty level
        
        return baseScore + timeBonus + difficultyBonus;
    }

    private void updateLiveLeaderboard(Long gameId, Long userId, int pointsEarned) {
        try {
            String leaderboardKey = "game:leaderboard:" + gameId;
            redisTemplate.opsForZSet().incrementScore(leaderboardKey, userId.toString(), pointsEarned);
            
            // Set expiry for the leaderboard
            redisTemplate.expire(leaderboardKey, Duration.ofHours(24));
            
            // Get current rankings
            List<LeaderboardEntry> currentRankings = getCurrentRankings(gameId);
            
            // Broadcast leaderboard update
            LeaderboardUpdate leaderboardUpdate = LeaderboardUpdate.newBuilder()
                    .gameId(gameId)
                    .rankings(currentRankings)
                    .timestamp(LocalDateTime.now())
                    .build();
                    
            broadcastLeaderboardUpdate(leaderboardUpdate);
            
        } catch (Exception e) {
            log.error("Error updating live leaderboard: gameId={}, userId={}, error={}", 
                     gameId, userId, e.getMessage(), e);
        }
    }

    private List<LeaderboardEntry> getCurrentRankings(Long gameId) {
        // Implementation would get current rankings from Redis
        // This is a placeholder - actual implementation would fetch from Redis ZSet
        return List.of();
    }

    private boolean allPlayersAnswered(Long gameId, Long questionId) {
        try {
            List<GamePlayer> activePlayers = gamePlayerRepository.findByGameIdAndLeftAtIsNull(gameId);
            
            // Check if all active players have submitted answers for this question
            // This would require tracking answer submissions in Redis or database
            String answersKey = "game:answers:" + gameId + ":" + questionId;
            Long answeredCount = redisTemplate.opsForSet().size(answersKey);
            
            return answeredCount != null && answeredCount >= activePlayers.size();
            
        } catch (Exception e) {
            log.error("Error checking if all players answered: gameId={}, questionId={}, error={}", 
                     gameId, questionId, e.getMessage(), e);
            return false;
        }
    }

    private void completeCurrentQuestion(Long gameId) {
        try {
            Game game = gameRepository.findOne(gameId);
            if (game == null) {
                return;
            }
            
            int currentQuestionIndex = game.getQuestionsAnswered();
            
            // Check if this was the last question
            if (currentQuestionIndex >= game.getGameQuestions().size() - 1) {
                endLiveGame(gameId, GameEndReason.COMPLETED);
            } else {
                // Progress to next question
                progressToNextQuestion(game, currentQuestionIndex);
            }
            
        } catch (Exception e) {
            log.error("Error completing current question: gameId={}, error={}", gameId, e.getMessage(), e);
        }
    }

    private void progressToNextQuestion(Game game, Integer currentQuestionIndex) {
        try {
            int nextQuestionIndex = currentQuestionIndex + 1;
            
            if (nextQuestionIndex < game.getGameQuestions().size()) {
                // Start timer for next question
                startQuestionTimer(game, nextQuestionIndex);
                
                // Next question will be distributed via GraphQL subscriptions through GameEventPublisher
                // This eliminates duplicate WebSocket messaging while maintaining real-time question progression
                log.debug("Next question will be distributed via GraphQL subscriptions: gameId={}, questionIndex={}",
                        game.getId(), nextQuestionIndex);
                
                // Broadcast question change
                GameStateUpdate questionUpdate = GameStateUpdate.newBuilder()
                        .gameId(game.getId())
                        .status(GameStatus.ACTIVE)
                        .currentQuestionIndex(nextQuestionIndex)
                        .totalQuestions(game.getGameQuestions().size())
                        .timestamp(LocalDateTime.now())
                        .eventType(GameEventType.QUESTION_CHANGED)
                        .build();
                        
                broadcastGameStateUpdate(questionUpdate);
                
            } else {
                // No more questions - end the game
                endLiveGame(game.getId(), GameEndReason.COMPLETED);
            }
            
        } catch (Exception e) {
            log.error("Error progressing to next question: gameId={}, currentIndex={}, error={}", 
                     game.getId(), currentQuestionIndex, e.getMessage(), e);
        }
    }

    private void markUnansweredQuestionsTimedOut(Long gameId, Integer questionIndex) {
        try {
            // Mark players who haven't answered as timed out
            String answersKey = "game:answers:" + gameId + ":" + questionIndex;
            List<GamePlayer> activePlayers = gamePlayerRepository.findByGameIdAndLeftAtIsNull(gameId);
            
            for (GamePlayer player : activePlayers) {
                Boolean hasAnswered = redisTemplate.opsForSet().isMember(answersKey, player.getUser().getId().toString());
                if (!Boolean.TRUE.equals(hasAnswered)) {
                    // Record timeout for this player
                    recordPlayerTimeout(gameId, player.getUser().getId(), questionIndex);
                }
            }
            
        } catch (Exception e) {
            log.error("Error marking unanswered questions timed out: gameId={}, questionIndex={}, error={}", 
                     gameId, questionIndex, e.getMessage(), e);
        }
    }

    private void recordPlayerTimeout(Long gameId, Long userId, Integer questionIndex) {
        try {
            // Record timeout in Redis or database
            String timeoutKey = "game:timeouts:" + gameId + ":" + questionIndex;
            redisTemplate.opsForSet().add(timeoutKey, userId.toString());
            redisTemplate.expire(timeoutKey, Duration.ofHours(24));
            
            // Broadcast timeout event
            PlayerTimeout timeout = PlayerTimeout.newBuilder()
                    .gameId(gameId)
                    .userId(userId)
                    .questionIndex(questionIndex)
                    .timestamp(LocalDateTime.now())
                    .build();
                    
            broadcastPlayerTimeout(timeout);
            
        } catch (Exception e) {
            log.error("Error recording player timeout: gameId={}, userId={}, questionIndex={}, error={}", 
                     gameId, userId, questionIndex, e.getMessage(), e);
        }
    }

    private List<PlayerScore> calculateFinalScores(Long gameId) {
        try {
            String leaderboardKey = "game:leaderboard:" + gameId;
            var topScores = redisTemplate.opsForZSet().reverseRangeWithScores(leaderboardKey, 0, -1);
            
            if (topScores == null || topScores.isEmpty()) {
                return List.of();
            }
            
            // Convert to list for indexing
            var scoreList = new java.util.ArrayList<>(topScores);
            
            return scoreList.stream()
                    .map(tuple -> {
                        // Safely cast tuple.getValue() to String, then to Long
                        Long userId = Long.valueOf(tuple.getValue().toString());
                        Integer score = tuple.getScore() != null ? tuple.getScore().intValue() : 0;
                        Integer rank = scoreList.indexOf(tuple) + 1;
                        
                        return PlayerScore.newBuilder()
                                .userId(userId)
                                .score(score)
                                .rank(rank)
                                .build();
                    })
                    .collect(java.util.stream.Collectors.toList());
                    
        } catch (Exception e) {
            log.error("Error calculating final scores for game {}: {}", gameId, e.getMessage(), e);
            return List.of();
        }
    }

    private void updatePersistentLeaderboards(Long gameId, List<PlayerScore> finalScores) {
        try {
            for (PlayerScore score : finalScores) {
                // PlayerScore uses primitive types, so no null check needed
                leaderboardService.addScore(String.valueOf(score.getUserId()), (double) score.getScore());
            }
            
        } catch (Exception e) {
            log.error("Error updating persistent leaderboards for game {}: {}", gameId, e.getMessage(), e);
        }
    }

    @Async
    protected void handleTimerExpiration(Long gameId, Integer questionIndex) {
        forceQuestionProgression(gameId, questionIndex);
    }

    private void sendTimerUpdate(Long gameId, GameTimer timer, long currentTime) {
        try {
            int remainingSeconds = (int) ((timer.getEndTime() - currentTime) / 1000);
            
            TimerUpdate timerUpdate = TimerUpdate.newBuilder()
                    .gameId(gameId)
                    .questionIndex(timer.getQuestionIndex())
                    .remainingSeconds(Math.max(0, remainingSeconds))
                    .totalSeconds(timer.getDuration())
                    .isWarning(remainingSeconds <= COUNTDOWN_WARNING_SECONDS)
                    .timestamp(LocalDateTime.now())
                    .build();
                    
            broadcastTimerUpdate(timerUpdate);
            
        } catch (Exception e) {
            log.error("Error sending timer update: gameId={}, error={}", gameId, e.getMessage(), e);
        }
    }

    /**
     * Handle player joining a game with proper game logic and WebSocket notifications
     */
    public void handlePlayerJoined(Game game, GamePlayer player) {
        try {
            log.debug("Handling player joined: gameId={}, playerId={}", game.getId(), player.getId());
            
            // 1. Update Redis state for player in lobby
            addPlayerToRedisLobby(game.getId(), player.getUser().getId());
            
            // 2. Update live leaderboard with initial score
            updateLiveLeaderboard(game.getId(), player.getUser().getId(), 0);
            
            // 3. Player join events will be broadcast via GraphQL subscriptions through GameEventPublisher
            // This eliminates duplicate WebSocket messaging while maintaining real-time player updates
            log.debug("Player join will be broadcast via GraphQL subscriptions: gameId={}, playerId={}",
                     game.getId(), player.getId());
            
            log.debug("Player join handled successfully: gameId={}, playerId={}", game.getId(), player.getId());
        } catch (Exception e) {
            log.error("Error handling player joined: gameId={}, playerId={}, error={}", 
                     game.getId(), player.getId(), e.getMessage(), e);
            // Don't rethrow - allow game to continue
        }
    }

    /**
     * Handle player leaving a game with proper cleanup and WebSocket notifications
     */
    public void handlePlayerLeft(Game game, GamePlayer player) {
        try {
            log.debug("Handling player left: gameId={}, playerId={}", game.getId(), player.getId());
            
            // 1. Remove player from Redis lobby
            removePlayerFromRedisLobby(game.getId(), player.getUser().getId());
            
            // 2. Clean up player's answers and timeouts
            cleanupPlayerGameData(game.getId(), player.getUser().getId());
            
            // 3. Player leave events will be broadcast via GraphQL subscriptions through GameEventPublisher
            // This eliminates duplicate WebSocket messaging while maintaining real-time player updates
            log.debug("Player leave will be broadcast via GraphQL subscriptions: gameId={}, playerId={}",
                     game.getId(), player.getId());
            
            log.debug("Player left handled successfully: gameId={}, playerId={}", game.getId(), player.getId());
        } catch (Exception e) {
            log.error("Error handling player left: gameId={}, playerId={}, error={}", 
                     game.getId(), player.getId(), e.getMessage(), e);
            // Don't rethrow - allow game to continue
        }
    }

    /**
     * Handle player ready status changes with WebSocket notifications
     */
    public void handlePlayerReadyStatusChange(Game game, GamePlayer player, boolean ready) {
        try {
            log.debug("Handling player ready status change: gameId={}, playerId={}, ready={}", 
                     game.getId(), player.getId(), ready);
            
            // 1. Update Redis ready state
            updatePlayerReadyState(game.getId(), player.getUser().getId(), ready);
            
            // 2. Create ready status event data
            Map<String, Object> readyStatusData = Map.of(
                "gameId", game.getId(),
                "userId", player.getUser().getId(),
                "username", player.getUser().getUsername(),
                "ready", ready,
                "timestamp", LocalDateTime.now()
            );
            
            // 3. Player readiness changes will be broadcast via GraphQL subscriptions through GameEventPublisher
            // This eliminates duplicate WebSocket messaging while maintaining real-time readiness updates
            log.debug("Player readiness change will be broadcast via GraphQL subscriptions: gameId={}, playerId={}, ready={}",
                     game.getId(), player.getId(), ready);
            
            log.debug("Player ready status change handled successfully: gameId={}, playerId={}, ready={}", 
                     game.getId(), player.getId(), ready);
        } catch (Exception e) {
            log.error("Error handling player ready status change: gameId={}, playerId={}, ready={}, error={}", 
                     game.getId(), player.getId(), ready, e.getMessage(), e);
            // Don't rethrow - allow game to continue
        }
    }

    /**
     * Handle game state changes (pause/resume/abandon) with proper Redis updates
     */
    public void handleGameStateChange(Game game, Game.GameStatus previousStatus) {
        try {
            log.debug("Handling game state change: gameId={}, {} -> {}", 
                     game.getId(), previousStatus, game.getStatus());
            
            // 1. Update Redis game state
            updateGameStateInRedis(game);
            
            // 2. Handle timer state based on status change
            handleTimerStateChange(game.getId(), game.getStatus());
            
            // 3. Game state changes will be broadcast via GraphQL subscriptions through GameEventPublisher
            // This eliminates duplicate WebSocket messaging while maintaining real-time state updates
            log.debug("Game state change will be broadcast via GraphQL subscriptions: gameId={}, {} -> {}",
                     game.getId(), previousStatus, game.getStatus());
            
            log.debug("Game state change handled successfully: gameId={}, {} -> {}", 
                     game.getId(), previousStatus, game.getStatus());
        } catch (Exception e) {
            log.error("Error handling game state change: gameId={}, {} -> {}, error={}", 
                     game.getId(), previousStatus, game.getStatus(), e.getMessage(), e);
            // Don't rethrow - allow game to continue
        }
    }

    /**
     * Handle generic real-time events during gameplay
     */
    public void handleRealtimeEvent(Long gameId, String eventType, Object eventData) {
        try {
            log.debug("Handling realtime event: gameId={}, eventType={}", gameId, eventType);
            
            // 1. Update Redis state based on event type if needed
            updateRedisForRealtimeEvent(gameId, eventType, eventData);
            
            // 2. Real-time events will be broadcast via GraphQL subscriptions through GameEventPublisher
            // This eliminates duplicate WebSocket messaging while maintaining comprehensive event handling
            log.debug("Real-time event will be broadcast via GraphQL subscriptions: gameId={}, eventType={}",
                     gameId, eventType);
            
            log.debug("Realtime event handled successfully: gameId={}, eventType={}", gameId, eventType);
        } catch (Exception e) {
            log.error("Error handling realtime event: gameId={}, eventType={}, error={}", 
                     gameId, eventType, e.getMessage(), e);
            // Don't rethrow - allow game to continue
        }
    }

    // Redis helper methods for player and game state management

    private void addPlayerToRedisLobby(Long gameId, Long userId) {
        try {
            String lobbyKey = "game:lobby:" + gameId;
            redisTemplate.opsForSet().add(lobbyKey, userId.toString());
            redisTemplate.expire(lobbyKey, Duration.ofHours(24));
            log.debug("Player added to Redis lobby: gameId={}, userId={}", gameId, userId);
        } catch (Exception e) {
            log.error("Error adding player to Redis lobby: gameId={}, userId={}, error={}", 
                     gameId, userId, e.getMessage(), e);
        }
    }

    private void removePlayerFromRedisLobby(Long gameId, Long userId) {
        try {
            String lobbyKey = "game:lobby:" + gameId;
            redisTemplate.opsForSet().remove(lobbyKey, userId.toString());
            log.debug("Player removed from Redis lobby: gameId={}, userId={}", gameId, userId);
        } catch (Exception e) {
            log.error("Error removing player from Redis lobby: gameId={}, userId={}, error={}", 
                     gameId, userId, e.getMessage(), e);
        }
    }

    private void cleanupPlayerGameData(Long gameId, Long userId) {
        try {
            // Clean up answer tracking
            String answersPattern = "game:answers:" + gameId + ":*";
            var answerKeys = redisTemplate.keys(answersPattern);
            if (answerKeys != null) {
                for (String key : answerKeys) {
                    redisTemplate.opsForSet().remove(key, userId.toString());
                }
            }
            
            // Clean up timeout tracking
            String timeoutPattern = "game:timeouts:" + gameId + ":*";
            var timeoutKeys = redisTemplate.keys(timeoutPattern);
            if (timeoutKeys != null) {
                for (String key : timeoutKeys) {
                    redisTemplate.opsForSet().remove(key, userId.toString());
                }
            }
            
            log.debug("Player game data cleaned up: gameId={}, userId={}", gameId, userId);
        } catch (Exception e) {
            log.error("Error cleaning up player game data: gameId={}, userId={}, error={}", 
                     gameId, userId, e.getMessage(), e);
        }
    }

    private void updatePlayerReadyState(Long gameId, Long userId, boolean ready) {
        try {
            String readyKey = "game:ready:" + gameId;
            if (ready) {
                redisTemplate.opsForSet().add(readyKey, userId.toString());
            } else {
                redisTemplate.opsForSet().remove(readyKey, userId.toString());
            }
            redisTemplate.expire(readyKey, Duration.ofHours(24));
            log.debug("Player ready state updated: gameId={}, userId={}, ready={}", gameId, userId, ready);
        } catch (Exception e) {
            log.error("Error updating player ready state: gameId={}, userId={}, ready={}, error={}", 
                     gameId, userId, ready, e.getMessage(), e);
        }
    }

    private void updateGameStateInRedis(Game game) {
        try {
            String stateKey = GAME_STATE_PREFIX + game.getId();
            GameStateCache gameState = GameStateCache.builder()
                    .gameId(game.getId())
                    .status(game.getStatus())
                    .currentQuestionIndex(game.getQuestionsAnswered())
                    .totalQuestions(game.getGameQuestions().size())
                    .playerCount(game.getCurrentPlayers())
                    .lastUpdated(LocalDateTime.now())
                    .build();
                    
            redisTemplate.opsForValue().set(stateKey, gameState, Duration.ofHours(2));
            log.debug("Game state updated in Redis: gameId={}, status={}", game.getId(), game.getStatus());
        } catch (Exception e) {
            log.error("Error updating game state in Redis: gameId={}, error={}", game.getId(), e.getMessage(), e);
        }
    }

    private void handleTimerStateChange(Long gameId, Game.GameStatus status) {
        try {
            GameTimer timer = activeTimers.get(gameId);
            
            switch (status) {
                case PAUSED:
                    if (timer != null) {
                        // Pause logic - could store remaining time
                        log.debug("Game timer paused: gameId={}", gameId);
                    }
                    break;
                case IN_PROGRESS:
                    // Resume logic - could restore timer with remaining time
                    log.debug("Game timer resumed: gameId={}", gameId);
                    break;
                case ABANDONED, COMPLETED:
                    // Stop and clean up timer
                    if (timer != null) {
                        activeTimers.remove(gameId);
                        redisTemplate.delete(GAME_TIMER_PREFIX + gameId);
                        log.debug("Game timer stopped and cleaned up: gameId={}", gameId);
                    }
                    break;
                default:
                    // No timer action needed
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling timer state change: gameId={}, status={}, error={}", 
                     gameId, status, e.getMessage(), e);
        }
    }

    private void updateRedisForRealtimeEvent(Long gameId, String eventType, Object eventData) {
        try {
            // Handle specific event types that require Redis updates
            switch (eventType) {
                case "game.created":
                    // Update game discovery cache
                    String discoveryKey = "games:active";
                    redisTemplate.opsForSet().add(discoveryKey, gameId.toString());
                    redisTemplate.expire(discoveryKey, Duration.ofHours(24));
                    break;
                case "player.readiness.changed":
                    // Already handled in handlePlayerReadyStatusChange
                    break;
                default:
                    // No specific Redis update needed for this event type
                    break;
            }
            
            log.debug("Redis updated for realtime event: gameId={}, eventType={}", gameId, eventType);
        } catch (Exception e) {
            log.error("Error updating Redis for realtime event: gameId={}, eventType={}, error={}", 
                     gameId, eventType, e.getMessage(), e);
        }
    }

    private void cleanupGameRedisData(Long gameId) {
        try {
            // Clean up game state
            redisTemplate.delete(GAME_STATE_PREFIX + gameId);
            
            // Clean up lobby data
            redisTemplate.delete("game:lobby:" + gameId);
            
            // Clean up ready state
            redisTemplate.delete("game:ready:" + gameId);
            
            // Clean up leaderboard
            redisTemplate.delete("game:leaderboard:" + gameId);
            
            // Clean up answer tracking
            String answersPattern = "game:answers:" + gameId + ":*";
            var answerKeys = redisTemplate.keys(answersPattern);
            if (answerKeys != null && !answerKeys.isEmpty()) {
                redisTemplate.delete(answerKeys);
            }
            
            // Clean up timeout tracking
            String timeoutPattern = "game:timeouts:" + gameId + ":*";
            var timeoutKeys = redisTemplate.keys(timeoutPattern);
            if (timeoutKeys != null && !timeoutKeys.isEmpty()) {
                redisTemplate.delete(timeoutKeys);
            }
            
            // Remove from active games discovery
            redisTemplate.opsForSet().remove("games:active", gameId.toString());
            
            log.debug("Game Redis data cleaned up: gameId={}", gameId);
        } catch (Exception e) {
            log.error("Error cleaning up game Redis data: gameId={}, error={}", gameId, e.getMessage(), e);
        }
    }

    // Broadcasting methods (delegating to other services)

    private void broadcastGameStateUpdate(GameStateUpdate update) {
        try {
            // Broadcast exclusively via GraphQL subscriptions - eliminates duplicate events
            gameEventPublisher.publishGameStateUpdate(update);
            log.debug("Game state update published via GraphQL subscriptions: gameId={}, eventType={}",
                     update.getGameId(), update.getEventType());
        } catch (Exception e) {
            log.error("Error broadcasting game state update: {}", e.getMessage(), e);
        }
    }

    private void broadcastAnswerResult(AnswerResult result) {
        try {
            // Broadcast exclusively via GraphQL subscriptions - eliminates duplicate events
            gameEventPublisher.publishAnswerResult(result);
            log.debug("Answer result published via GraphQL subscriptions: gameId={}, userId={}, isCorrect={}",
                     result.getGameId(), result.getUserId(), result.getIsCorrect());
        } catch (Exception e) {
            log.error("Error broadcasting answer result: {}", e.getMessage(), e);
        }
    }

    private void broadcastTimerUpdate(TimerUpdate update) {
        try {
            // Broadcast exclusively via GraphQL subscriptions - eliminates duplicate events
            gameEventPublisher.publishTimerUpdate(update);
            log.debug("Timer update published via GraphQL subscriptions: gameId={}, remainingSeconds={}",
                     update.getGameId(), update.getRemainingSeconds());
        } catch (Exception e) {
            log.error("Error broadcasting timer update: {}", e.getMessage(), e);
        }
    }

    private void broadcastLeaderboardUpdate(LeaderboardUpdate update) {
        try {
            // Broadcast exclusively via GraphQL subscriptions - eliminates duplicate events
            gameEventPublisher.publishLeaderboardUpdate(update);
            log.debug("Leaderboard update published via GraphQL subscriptions: gameId={}",
                     update.getGameId());
        } catch (Exception e) {
            log.error("Error broadcasting leaderboard update: {}", e.getMessage(), e);
        }
    }

    private void broadcastPlayerTimeout(PlayerTimeout timeout) {
        try {
            // Broadcast exclusively via GraphQL subscriptions - eliminates duplicate events
            gameEventPublisher.publishPlayerTimeout(timeout);
            log.debug("Player timeout published via GraphQL subscriptions: gameId={}, userId={}",
                     timeout.getGameId(), timeout.getUserId());
        } catch (Exception e) {
            log.error("Error broadcasting player timeout: {}", e.getMessage(), e);
        }
    }

    /**
     * Inner class representing a game timer
     */
    private static class GameTimer {
        private final Long gameId;
        private final Integer questionIndex;
        private final int duration;
        private final long startTime;
        private final long endTime;
        private boolean updateSent = false;

        public GameTimer(Long gameId, Integer questionIndex, int durationSeconds) {
            this.gameId = gameId;
            this.questionIndex = questionIndex;
            this.duration = durationSeconds;
            this.startTime = System.currentTimeMillis();
            this.endTime = startTime + (durationSeconds * 1000L);
        }

        public boolean isExpired(long currentTime) {
            return currentTime >= endTime;
        }

        public boolean shouldSendUpdate(long currentTime) {
            int remainingSeconds = (int) ((endTime - currentTime) / 1000);
            return !updateSent && remainingSeconds <= COUNTDOWN_WARNING_SECONDS && remainingSeconds > 0;
        }

        public void markUpdateSent() {
            this.updateSent = true;
        }

        public void stop() {
            // Timer stopped - no cleanup needed for this simple implementation
        }

        // Getters
        public Long getGameId() { return gameId; }
        public Integer getQuestionIndex() { return questionIndex; }
        public int getDuration() { return duration; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
    }

    /**
     * Inner class for caching game state in Redis
     */
    @lombok.Builder
    @lombok.Data
    private static class GameStateCache {
        private Long gameId;
        private Game.GameStatus status;
        private Integer currentQuestionIndex;
        private Integer totalQuestions;
        private Integer playerCount;
        private LocalDateTime lastUpdated;
    }
}