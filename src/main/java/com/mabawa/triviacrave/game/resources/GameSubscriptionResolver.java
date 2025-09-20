package com.mabawa.triviacrave.game.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mabawa.triviacrave.game.service.GameEventPublisher;
import com.mabawa.triviacrave.game.service.GameEventPublisher.GameEventMessage;
import com.mabawa.triviacrave.game.service.LiveGameOrchestrator;
import com.mabawa.triviacrave.game.service.WebSocketMessageService;
import com.mabawa.triviacrave.generated.graphql.types.*;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsSubscription;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GraphQL subscription resolver for real-time game events using Netflix DGS.
 * Handles subscription lifecycle, authentication, and event filtering.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class GameSubscriptionResolver {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer messageListenerContainer;
    private final LiveGameOrchestrator liveGameOrchestrator;
    private final WebSocketMessageService webSocketMessageService;
    private final GameEventPublisher gameEventPublisher;
    private final ObjectMapper objectMapper;

    // Track active subscription sinks
    private final Map<String, FluxSink<GameStateUpdate>> gameStateSinks = new ConcurrentHashMap<>();
    private final Map<String, FluxSink<LobbyUpdate>> lobbySinks = new ConcurrentHashMap<>();
    private final Map<String, FluxSink<LeaderboardUpdate>> leaderboardSinks = new ConcurrentHashMap<>();
    private final Map<String, FluxSink<TimerUpdate>> timerSinks = new ConcurrentHashMap<>();
    private final Map<String, FluxSink<AnswerResult>> answerResultSinks = new ConcurrentHashMap<>();
    private final Map<String, FluxSink<PlayerTimeout>> timeoutSinks = new ConcurrentHashMap<>();

    private final Map<String, SubscriptionSession> activeSessions = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        log.info("GameSubscriptionResolver initialized");
    }

    @PreDestroy
    public void cleanup() {
        try {
            // Clean up all active sinks
            gameStateSinks.values().forEach(FluxSink::complete);
            lobbySinks.values().forEach(FluxSink::complete);
            leaderboardSinks.values().forEach(FluxSink::complete);
            timerSinks.values().forEach(FluxSink::complete);
            answerResultSinks.values().forEach(FluxSink::complete);
            timeoutSinks.values().forEach(FluxSink::complete);

            gameStateSinks.clear();
            lobbySinks.clear();
            leaderboardSinks.clear();
            timerSinks.clear();
            answerResultSinks.clear();
            timeoutSinks.clear();
            activeSessions.clear();

            log.info("GameSubscriptionResolver cleanup completed");
        } catch (Exception e) {
            log.error("Error during GameSubscriptionResolver cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Subscribe to game state updates (start, end, question progression)
     */
    @DgsSubscription
    @PreAuthorize("isAuthenticated()")
    public Publisher<GameStateUpdate> gameStateUpdates(@InputArgument GameSubscriptionFilter filter) {
        return Flux.<GameStateUpdate>create(sink -> {
            try {
                Long userId = getCurrentUserId();
                String sessionKey = generateSessionKey("gameState", filter.getGameId(), userId);
                
                // Validate user access to game
                if (!hasGameAccess(userId, filter.getGameId())) {
                    sink.error(new RuntimeException("Access denied to game: " + filter.getGameId()));
                    return;
                }

                // Register subscription session
                SubscriptionSession session = SubscriptionSession.builder()
                        .sessionKey(sessionKey)
                        .userId(userId)
                        .gameId(filter.getGameId())
                        .subscriptionType("GAME_STATE")
                        .connectedAt(LocalDateTime.now())
                        .lastActivity(LocalDateTime.now())
                        .build();

                activeSessions.put(sessionKey, session);
                gameStateSinks.put(sessionKey, sink);

                // Register WebSocket session
                webSocketMessageService.registerUserSession(sessionKey, userId, filter.getGameId());

                // Setup Redis listener for this game
                setupGameStateListener(filter.getGameId(), sessionKey);

                // Send initial connection status
                ConnectionStatus connectionStatus = ConnectionStatus.newBuilder()
                        .isConnected(true)
                        .gameId(filter.getGameId())
                        .userId(userId)
                        .connectionTime(LocalDateTime.now())
                        .build();

                gameEventPublisher.publishConnectionStatus(filter.getGameId(), userId, true);

                log.info("Game state subscription created: gameId={}, userId={}, sessionKey={}", 
                        filter.getGameId(), userId, sessionKey);

                // Handle subscription cleanup
                sink.onCancel(() -> {
                    cleanupSubscription(sessionKey, "gameState");
                    gameEventPublisher.publishConnectionStatus(filter.getGameId(), userId, false);
                });

                sink.onDispose(() -> {
                    cleanupSubscription(sessionKey, "gameState");
                    gameEventPublisher.publishConnectionStatus(filter.getGameId(), userId, false);
                });

            } catch (Exception e) {
                log.error("Error creating game state subscription: {}", e.getMessage(), e);
                sink.error(e);
            }
        })
        .doOnSubscribe(subscription -> log.debug("Game state subscription started: gameId={}", filter.getGameId()))
        .doOnCancel(() -> log.debug("Game state subscription cancelled: gameId={}", filter.getGameId()))
        .doFinally(signalType -> log.debug("Game state subscription ended: gameId={}, signal={}", filter.getGameId(), signalType));
    }

    /**
     * Subscribe to lobby updates (player join/leave, ready status)
     */
    @DgsSubscription
    @PreAuthorize("isAuthenticated()")
    public Publisher<LobbyUpdate> lobbyUpdates(@InputArgument LobbySubscriptionFilter filter) {
        return Flux.<LobbyUpdate>create(sink -> {
            try {
                Long userId = getCurrentUserId();
                String sessionKey = generateSessionKey("lobby", filter.getGameId(), userId);

                if (!hasGameAccess(userId, filter.getGameId())) {
                    sink.error(new RuntimeException("Access denied to game: " + filter.getGameId()));
                    return;
                }

                SubscriptionSession session = SubscriptionSession.builder()
                        .sessionKey(sessionKey)
                        .userId(userId)
                        .gameId(filter.getGameId())
                        .subscriptionType("LOBBY")
                        .connectedAt(LocalDateTime.now())
                        .lastActivity(LocalDateTime.now())
                        .build();

                activeSessions.put(sessionKey, session);
                lobbySinks.put(sessionKey, sink);

                setupLobbyListener(filter.getGameId(), sessionKey);

                log.info("Lobby subscription created: gameId={}, userId={}", filter.getGameId(), userId);

                sink.onCancel(() -> cleanupSubscription(sessionKey, "lobby"));
                sink.onDispose(() -> cleanupSubscription(sessionKey, "lobby"));

            } catch (Exception e) {
                log.error("Error creating lobby subscription: {}", e.getMessage(), e);
                sink.error(e);
            }
        })
        .doOnSubscribe(subscription -> log.debug("Lobby subscription started: gameId={}", filter.getGameId()))
        .doOnCancel(() -> log.debug("Lobby subscription cancelled: gameId={}", filter.getGameId()));
    }

    /**
     * Subscribe to live leaderboard changes during gameplay
     */
    @DgsSubscription
    @PreAuthorize("isAuthenticated()")
    public Publisher<LeaderboardUpdate> leaderboardUpdates(@InputArgument LeaderboardSubscriptionFilter filter) {
        return Flux.<LeaderboardUpdate>create(sink -> {
            try {
                Long userId = getCurrentUserId();
                String sessionKey = generateSessionKey("leaderboard", filter.getGameId(), userId);

                if (!hasGameAccess(userId, filter.getGameId())) {
                    sink.error(new RuntimeException("Access denied to game: " + filter.getGameId()));
                    return;
                }

                SubscriptionSession session = SubscriptionSession.builder()
                        .sessionKey(sessionKey)
                        .userId(userId)
                        .gameId(filter.getGameId())
                        .subscriptionType("LEADERBOARD")
                        .connectedAt(LocalDateTime.now())
                        .lastActivity(LocalDateTime.now())
                        .build();

                activeSessions.put(sessionKey, session);
                leaderboardSinks.put(sessionKey, sink);

                setupLeaderboardListener(filter.getGameId(), sessionKey);

                log.info("Leaderboard subscription created: gameId={}, userId={}", filter.getGameId(), userId);

                sink.onCancel(() -> cleanupSubscription(sessionKey, "leaderboard"));
                sink.onDispose(() -> cleanupSubscription(sessionKey, "leaderboard"));

            } catch (Exception e) {
                log.error("Error creating leaderboard subscription: {}", e.getMessage(), e);
                sink.error(e);
            }
        })
        .doOnSubscribe(subscription -> log.debug("Leaderboard subscription started: gameId={}", filter.getGameId()))
        .doOnCancel(() -> log.debug("Leaderboard subscription cancelled: gameId={}", filter.getGameId()));
    }

    /**
     * Subscribe to question timer countdown
     */
    @DgsSubscription
    @PreAuthorize("isAuthenticated()")
    public Publisher<TimerUpdate> timerUpdates(@InputArgument TimerSubscriptionFilter filter) {
        return Flux.<TimerUpdate>create(sink -> {
            try {
                Long userId = getCurrentUserId();
                String sessionKey = generateSessionKey("timer", filter.getGameId(), userId);

                if (!hasGameAccess(userId, filter.getGameId())) {
                    sink.error(new RuntimeException("Access denied to game: " + filter.getGameId()));
                    return;
                }

                SubscriptionSession session = SubscriptionSession.builder()
                        .sessionKey(sessionKey)
                        .userId(userId)
                        .gameId(filter.getGameId())
                        .subscriptionType("TIMER")
                        .connectedAt(LocalDateTime.now())
                        .lastActivity(LocalDateTime.now())
                        .build();

                activeSessions.put(sessionKey, session);
                timerSinks.put(sessionKey, sink);

                setupTimerListener(filter.getGameId(), sessionKey);

                // Send current timer state immediately if available
                TimerUpdate currentTimer = liveGameOrchestrator.getCurrentTimerState(filter.getGameId());
                if (currentTimer != null) {
                    sink.next(currentTimer);
                }

                log.info("Timer subscription created: gameId={}, userId={}", filter.getGameId(), userId);

                sink.onCancel(() -> cleanupSubscription(sessionKey, "timer"));
                sink.onDispose(() -> cleanupSubscription(sessionKey, "timer"));

            } catch (Exception e) {
                log.error("Error creating timer subscription: {}", e.getMessage(), e);
                sink.error(e);
            }
        })
        .doOnSubscribe(subscription -> log.debug("Timer subscription started: gameId={}", filter.getGameId()))
        .doOnCancel(() -> log.debug("Timer subscription cancelled: gameId={}", filter.getGameId()));
    }

    /**
     * Subscribe to answer submissions and results
     */
    @DgsSubscription
    @PreAuthorize("isAuthenticated()")
    public Publisher<AnswerResult> answerResults(@InputArgument Long gameId) {
        return Flux.<AnswerResult>create(sink -> {
            try {
                Long userId = getCurrentUserId();
                String sessionKey = generateSessionKey("answerResult", gameId, userId);

                if (!hasGameAccess(userId, gameId)) {
                    sink.error(new RuntimeException("Access denied to game: " + gameId));
                    return;
                }

                SubscriptionSession session = SubscriptionSession.builder()
                        .sessionKey(sessionKey)
                        .userId(userId)
                        .gameId(gameId)
                        .subscriptionType("ANSWER_RESULT")
                        .connectedAt(LocalDateTime.now())
                        .lastActivity(LocalDateTime.now())
                        .build();

                activeSessions.put(sessionKey, session);
                answerResultSinks.put(sessionKey, sink);

                setupAnswerResultListener(gameId, sessionKey);

                log.info("Answer result subscription created: gameId={}, userId={}", gameId, userId);

                sink.onCancel(() -> cleanupSubscription(sessionKey, "answerResult"));
                sink.onDispose(() -> cleanupSubscription(sessionKey, "answerResult"));

            } catch (Exception e) {
                log.error("Error creating answer result subscription: {}", e.getMessage(), e);
                sink.error(e);
            }
        })
        .doOnSubscribe(subscription -> log.debug("Answer result subscription started: gameId={}", gameId))
        .doOnCancel(() -> log.debug("Answer result subscription cancelled: gameId={}", gameId));
    }

    /**
     * Subscribe to player timeouts
     */
    @DgsSubscription
    @PreAuthorize("isAuthenticated()")
    public Publisher<PlayerTimeout> playerTimeouts(@InputArgument Long gameId) {
        return Flux.<PlayerTimeout>create(sink -> {
            try {
                Long userId = getCurrentUserId();
                String sessionKey = generateSessionKey("timeout", gameId, userId);

                if (!hasGameAccess(userId, gameId)) {
                    sink.error(new RuntimeException("Access denied to game: " + gameId));
                    return;
                }

                SubscriptionSession session = SubscriptionSession.builder()
                        .sessionKey(sessionKey)
                        .userId(userId)
                        .gameId(gameId)
                        .subscriptionType("TIMEOUT")
                        .connectedAt(LocalDateTime.now())
                        .lastActivity(LocalDateTime.now())
                        .build();

                activeSessions.put(sessionKey, session);
                timeoutSinks.put(sessionKey, sink);

                setupTimeoutListener(gameId, sessionKey);

                log.info("Timeout subscription created: gameId={}, userId={}", gameId, userId);

                sink.onCancel(() -> cleanupSubscription(sessionKey, "timeout"));
                sink.onDispose(() -> cleanupSubscription(sessionKey, "timeout"));

            } catch (Exception e) {
                log.error("Error creating timeout subscription: {}", e.getMessage(), e);
                sink.error(e);
            }
        })
        .doOnSubscribe(subscription -> log.debug("Timeout subscription started: gameId={}", gameId))
        .doOnCancel(() -> log.debug("Timeout subscription cancelled: gameId={}", gameId));
    }

    /**
     * Combined game events subscription
     */
    @DgsSubscription
    @PreAuthorize("isAuthenticated()")
    public Publisher<GameEvent> gameEvents(@InputArgument Long gameId, @InputArgument java.util.List<GameEventType> eventTypes) {
        return Flux.<GameEvent>create(sink -> {
            try {
                Long userId = getCurrentUserId();
                String sessionKey = generateSessionKey("gameEvents", gameId, userId);

                if (!hasGameAccess(userId, gameId)) {
                    sink.error(new RuntimeException("Access denied to game: " + gameId));
                    return;
                }

                // This would be implemented to combine multiple event streams
                // For now, we'll delegate to individual subscriptions

                log.info("Combined game events subscription created: gameId={}, userId={}, eventTypes={}", 
                        gameId, userId, eventTypes);

                sink.onCancel(() -> cleanupSubscription(sessionKey, "gameEvents"));
                sink.onDispose(() -> cleanupSubscription(sessionKey, "gameEvents"));

            } catch (Exception e) {
                log.error("Error creating combined game events subscription: {}", e.getMessage(), e);
                sink.error(e);
            }
        })
        .take(Duration.ofHours(2)); // Automatic timeout after 2 hours
    }

    // Private helper methods

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new RuntimeException("User not authenticated");
        }
        return Long.valueOf(auth.getName());
    }

    private boolean hasGameAccess(Long userId, Long gameId) {
        // Implement game access validation
        // This should check if the user is a participant in the game
        try {
            // For now, allow access - in production this should validate game membership
            return true;
        } catch (Exception e) {
            log.error("Error checking game access: userId={}, gameId={}, error={}", userId, gameId, e.getMessage(), e);
            return false;
        }
    }

    private String generateSessionKey(String subscriptionType, Long gameId, Long userId) {
        return String.format("%s:%d:%d:%d", subscriptionType, gameId, userId, System.currentTimeMillis());
    }

    private void cleanupSubscription(String sessionKey, String subscriptionType) {
        try {
            SubscriptionSession session = activeSessions.remove(sessionKey);
            if (session != null) {
                webSocketMessageService.unregisterUserSession(sessionKey);
                log.debug("Cleaned up subscription: sessionKey={}, type={}", sessionKey, subscriptionType);
            }

            // Remove from appropriate sink map
            switch (subscriptionType) {
                case "gameState" -> gameStateSinks.remove(sessionKey);
                case "lobby" -> lobbySinks.remove(sessionKey);
                case "leaderboard" -> leaderboardSinks.remove(sessionKey);
                case "timer" -> timerSinks.remove(sessionKey);
                case "answerResult" -> answerResultSinks.remove(sessionKey);
                case "timeout" -> timeoutSinks.remove(sessionKey);
            }

        } catch (Exception e) {
            log.error("Error cleaning up subscription: sessionKey={}, error={}", sessionKey, e.getMessage(), e);
        }
    }

    // Redis listener setup methods (simplified for brevity)

    private void setupGameStateListener(Long gameId, String sessionKey) {
        // Set up Redis listener for game state events
        String channel = "game:events:" + gameId;
        messageListenerContainer.addMessageListener(new GameStateMessageListener(sessionKey), new ChannelTopic(channel));
    }

    private void setupLobbyListener(Long gameId, String sessionKey) {
        String channel = "lobby:events:" + gameId;
        messageListenerContainer.addMessageListener(new LobbyMessageListener(sessionKey), new ChannelTopic(channel));
    }

    private void setupLeaderboardListener(Long gameId, String sessionKey) {
        String channel = "leaderboard:events:" + gameId;
        messageListenerContainer.addMessageListener(new LeaderboardMessageListener(sessionKey), new ChannelTopic(channel));
    }

    private void setupTimerListener(Long gameId, String sessionKey) {
        String channel = "timer:events:" + gameId;
        messageListenerContainer.addMessageListener(new TimerMessageListener(sessionKey), new ChannelTopic(channel));
    }

    private void setupAnswerResultListener(Long gameId, String sessionKey) {
        String channel = "game:events:" + gameId;
        messageListenerContainer.addMessageListener(new AnswerResultMessageListener(sessionKey), new ChannelTopic(channel));
    }

    private void setupTimeoutListener(Long gameId, String sessionKey) {
        String channel = "game:events:" + gameId;
        messageListenerContainer.addMessageListener(new TimeoutMessageListener(sessionKey), new ChannelTopic(channel));
    }

    // Message listener implementations

    private class GameStateMessageListener implements MessageListener {
        private final String sessionKey;

        public GameStateMessageListener(String sessionKey) {
            this.sessionKey = sessionKey;
        }

        @Override
        public void onMessage(Message message, byte[] pattern) {
            try {
                FluxSink<GameStateUpdate> sink = gameStateSinks.get(sessionKey);
                if (sink != null && !sink.isCancelled()) {
                    // Parse the Redis message and convert to GameStateUpdate
                    String messageBody = new String(message.getBody());
                    log.debug("Received game state message for session: {}, message: {}", sessionKey, messageBody);

                    // Parse the GameEventMessage from Redis
                    GameEventMessage eventMessage = objectMapper.readValue(messageBody, GameEventMessage.class);

                    if ("GAME_STATE_UPDATE".equals(eventMessage.getEventType()) && eventMessage.getData() != null) {
                        // The data field is a JSON string, parse it to GameStateUpdate
                        GameStateUpdate update = objectMapper.readValue(eventMessage.getData(), GameStateUpdate.class);

                        // THIS IS THE CRITICAL MISSING PIECE - Send to client!
                        sink.next(update);
                        log.debug("Game state update emitted to client: sessionKey={}, gameId={}, eventType={}",
                                sessionKey, update.getGameId(), update.getEventType());
                    }
                }
            } catch (Exception e) {
                log.error("Error processing game state message: sessionKey={}, error={}", sessionKey, e.getMessage(), e);
            }
        }
    }

    // Implemented message listeners for all subscription types

    private class LobbyMessageListener implements MessageListener {
        private final String sessionKey;

        public LobbyMessageListener(String sessionKey) {
            this.sessionKey = sessionKey;
        }

        @Override
        public void onMessage(Message message, byte[] pattern) {
            try {
                FluxSink<LobbyUpdate> sink = lobbySinks.get(sessionKey);
                if (sink != null && !sink.isCancelled()) {
                    String messageBody = new String(message.getBody());
                    log.debug("Received lobby message for session: {}, message: {}", sessionKey, messageBody);

                    GameEventMessage eventMessage = objectMapper.readValue(messageBody, GameEventMessage.class);

                    if ("LOBBY_UPDATE".equals(eventMessage.getEventType()) && eventMessage.getData() != null) {
                        LobbyUpdate update = objectMapper.readValue(eventMessage.getData(), LobbyUpdate.class);
                        sink.next(update);
                        log.debug("Lobby update emitted to client: sessionKey={}, gameId={}, eventType={}",
                                sessionKey, update.getGameId(), update.getEventType());
                    }
                }
            } catch (Exception e) {
                log.error("Error processing lobby message: sessionKey={}, error={}", sessionKey, e.getMessage(), e);
            }
        }
    }

    private class LeaderboardMessageListener implements MessageListener {
        private final String sessionKey;

        public LeaderboardMessageListener(String sessionKey) {
            this.sessionKey = sessionKey;
        }

        @Override
        public void onMessage(Message message, byte[] pattern) {
            try {
                FluxSink<LeaderboardUpdate> sink = leaderboardSinks.get(sessionKey);
                if (sink != null && !sink.isCancelled()) {
                    String messageBody = new String(message.getBody());
                    log.debug("Received leaderboard message for session: {}, message: {}", sessionKey, messageBody);

                    GameEventMessage eventMessage = objectMapper.readValue(messageBody, GameEventMessage.class);

                    if ("LEADERBOARD_UPDATE".equals(eventMessage.getEventType()) && eventMessage.getData() != null) {
                        LeaderboardUpdate update = objectMapper.readValue(eventMessage.getData(), LeaderboardUpdate.class);
                        sink.next(update);
                        log.debug("Leaderboard update emitted to client: sessionKey={}, gameId={}",
                                sessionKey, update.getGameId());
                    }
                }
            } catch (Exception e) {
                log.error("Error processing leaderboard message: sessionKey={}, error={}", sessionKey, e.getMessage(), e);
            }
        }
    }

    private class TimerMessageListener implements MessageListener {
        private final String sessionKey;

        public TimerMessageListener(String sessionKey) {
            this.sessionKey = sessionKey;
        }

        @Override
        public void onMessage(Message message, byte[] pattern) {
            try {
                FluxSink<TimerUpdate> sink = timerSinks.get(sessionKey);
                if (sink != null && !sink.isCancelled()) {
                    String messageBody = new String(message.getBody());
                    log.debug("Received timer message for session: {}, message: {}", sessionKey, messageBody);

                    GameEventMessage eventMessage = objectMapper.readValue(messageBody, GameEventMessage.class);

                    if ("TIMER_UPDATE".equals(eventMessage.getEventType()) && eventMessage.getData() != null) {
                        TimerUpdate update = objectMapper.readValue(eventMessage.getData(), TimerUpdate.class);
                        sink.next(update);
                        log.debug("Timer update emitted to client: sessionKey={}, gameId={}, remainingSeconds={}",
                                sessionKey, update.getGameId(), update.getRemainingSeconds());
                    }
                }
            } catch (Exception e) {
                log.error("Error processing timer message: sessionKey={}, error={}", sessionKey, e.getMessage(), e);
            }
        }
    }

    private class AnswerResultMessageListener implements MessageListener {
        private final String sessionKey;

        public AnswerResultMessageListener(String sessionKey) {
            this.sessionKey = sessionKey;
        }

        @Override
        public void onMessage(Message message, byte[] pattern) {
            try {
                FluxSink<AnswerResult> sink = answerResultSinks.get(sessionKey);
                if (sink != null && !sink.isCancelled()) {
                    String messageBody = new String(message.getBody());
                    log.debug("Received answer result message for session: {}, message: {}", sessionKey, messageBody);

                    GameEventMessage eventMessage = objectMapper.readValue(messageBody, GameEventMessage.class);

                    if ("ANSWER_RESULT".equals(eventMessage.getEventType()) && eventMessage.getData() != null) {
                        AnswerResult result = objectMapper.readValue(eventMessage.getData(), AnswerResult.class);
                        sink.next(result);
                        log.debug("Answer result emitted to client: sessionKey={}, gameId={}, userId={}, isCorrect={}",
                                sessionKey, result.getGameId(), result.getUserId(), result.getIsCorrect());
                    }
                }
            } catch (Exception e) {
                log.error("Error processing answer result message: sessionKey={}, error={}", sessionKey, e.getMessage(), e);
            }
        }
    }

    private class TimeoutMessageListener implements MessageListener {
        private final String sessionKey;

        public TimeoutMessageListener(String sessionKey) {
            this.sessionKey = sessionKey;
        }

        @Override
        public void onMessage(Message message, byte[] pattern) {
            try {
                FluxSink<PlayerTimeout> sink = timeoutSinks.get(sessionKey);
                if (sink != null && !sink.isCancelled()) {
                    String messageBody = new String(message.getBody());
                    log.debug("Received timeout message for session: {}, message: {}", sessionKey, messageBody);

                    GameEventMessage eventMessage = objectMapper.readValue(messageBody, GameEventMessage.class);

                    if ("PLAYER_TIMEOUT".equals(eventMessage.getEventType()) && eventMessage.getData() != null) {
                        PlayerTimeout timeout = objectMapper.readValue(eventMessage.getData(), PlayerTimeout.class);
                        sink.next(timeout);
                        log.debug("Player timeout emitted to client: sessionKey={}, gameId={}, userId={}",
                                sessionKey, timeout.getGameId(), timeout.getUserId());
                    }
                }
            } catch (Exception e) {
                log.error("Error processing timeout message: sessionKey={}, error={}", sessionKey, e.getMessage(), e);
            }
        }
    }

    /**
     * Subscription session tracking
     */
    @lombok.Builder
    @lombok.Data
    private static class SubscriptionSession {
        private String sessionKey;
        private Long userId;
        private Long gameId;
        private String subscriptionType;
        private LocalDateTime connectedAt;
        private LocalDateTime lastActivity;
    }
}