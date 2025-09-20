package com.mabawa.triviacrave.game.service.impl;

import com.mabawa.triviacrave.common.utils.IDGenerator;
import com.mabawa.triviacrave.game.entity.Category;
import com.mabawa.triviacrave.game.entity.Game;
import com.mabawa.triviacrave.game.entity.GamePlayer;
import com.mabawa.triviacrave.game.entity.GameQuestion;
import com.mabawa.triviacrave.game.entity.Question;
import com.mabawa.triviacrave.game.repository.*;
import com.mabawa.triviacrave.game.service.CategoryService;
import com.mabawa.triviacrave.game.service.GameService;
import com.mabawa.triviacrave.game.service.LiveGameOrchestrator;
import com.mabawa.triviacrave.game.service.QuestionService;
import com.mabawa.triviacrave.game.service.ScoreService;
import com.mabawa.triviacrave.game.service.redis.RedisLeaderboardService;
import com.mabawa.triviacrave.game.repository.redis.GameStateRepository;
import com.mabawa.triviacrave.generated.graphql.types.GameEndReason;
import com.mabawa.triviacrave.generated.graphql.types.*;
import com.mabawa.triviacrave.user.entity.User;
import com.mabawa.triviacrave.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {
    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final GameQuestionRepository gameQuestionRepository;
    private final UserService userService;
    private final CategoryService categoryService;
    private final QuestionService questionService;
    private final ScoreService scoreService;
    private final RedisLeaderboardService redisLeaderboardService;
    private final GameStateRepository gameStateRepository;
    private final LiveGameOrchestrator liveGameOrchestrator;
    
    // Self-injection for accessing proxy methods to handle @Transactional properly
    private GameService self;
    
    @Autowired
    @Lazy
    public void setSelf(GameService self) {
        this.self = self;
    }
    

    @Override
    @Transactional
    public ApiResponse createGame(CreateGameCmd cmd) {
        try {
            Long userId = getCurrentUserId();
            validateCreateGameInput(cmd, userId);

            User user = userService.getUserById(userId);
            if (user == null) {
                throw new IllegalArgumentException("User not found");
            }

            // Create new game
            Long gameId = IDGenerator.generateId();
            Game game = Game.builder()
                    .id(gameId)
                    .user(user)
                    .gameMode(mapToEntityGameMode(cmd.getMode()))
                    .status(Game.GameStatus.WAITING)
                    .score(0)
                    .questionsAnswered(0)
                    .correctAnswers(0)
                    .maxPlayers(cmd.getMaxPlayers() != null ? cmd.getMaxPlayers() : 1)
                    .currentPlayers(1)
                    .createdAt(LocalDateTime.now())
                    .build();

            // Set category
            if (cmd.getCategoryIds() != null && !cmd.getCategoryIds().isEmpty()) {
                com.mabawa.triviacrave.game.entity.Category category = categoryService.getCategoryEntityById(cmd.getCategoryIds().getFirst());
                if (category != null) {
                    game.setCategory(category);
                }
            }

            // Generate invite code for private or multiplayer games
            if (game.getMaxPlayers() > 1 || (cmd.getIsPrivate() != null && cmd.getIsPrivate())) {
                game.setInviteCode(generateInviteCode());
            }

            gameRepository.save(game);

            // Initialize Redis game state and lobby for multiplayer games
            initializeRedisGameState(game, userId);

            // Create host game player
            Long gamePlayerId = IDGenerator.generateId();
            GamePlayer hostPlayer = GamePlayer.builder()
                    .id(gamePlayerId)
                    .game(game)
                    .user(user)
                    .isHost(true)
                    .isReady(game.getMaxPlayers() == 1) // Single player auto-ready
                    .joinedAt(LocalDateTime.now())
                    .build();
            gamePlayerRepository.save(hostPlayer);
            
            // Set host as ready in Redis for multiplayer games
            updatePlayerReadyStatusInRedis(game, userId, hostPlayer.getIsReady());

            // Single player games start immediately
            if (game.getMaxPlayers() == 1) {
                return self.startGameInternal(game);
            }

            // For multiplayer games, notify that game was created (for lobby discovery)
            try {
                liveGameOrchestrator.handleRealtimeEvent(
                    game.getId(), 
                    "game.created", 
                    mapToGraphQLGame(game)
                );
                log.debug("LiveGameOrchestrator handled game creation: gameId={}", game.getId());
            } catch (Exception e) {
                log.warn("Failed to handle game creation via LiveGameOrchestrator: gameId={}, error={}", 
                        game.getId(), e.getMessage());
                // Continue without failing the game creation
            }

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Game created successfully")
                    .data(mapToGraphQLGame(game))
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for create game: {}", e.getMessage());
            return createErrorResponse(400, e.getMessage());
        } catch (Exception e) {
            log.error("Error creating game: {}", e.getMessage(), e);
            return createErrorResponse(500, "Failed to create game");
        }
    }

    @Override
    @Transactional
    public ApiResponse joinGame(JoinGameCmd cmd) {
        try {
            Long userId = getCurrentUserId();
            validateJoinGameInput(cmd, userId);

            Game game = findGameByIdOrInviteCode(cmd.getGameId(), cmd.getInviteCode());
            if (game == null) {
                throw new IllegalArgumentException("Game not found");
            }

            if (!game.isGameWaiting()) {
                throw new IllegalArgumentException("Game is not accepting new players");
            }

            if (game.isFull()) {
                throw new IllegalArgumentException("Game is full");
            }

            // Check if user already joined
            if (gamePlayerRepository.existsByGameIdAndUserIdAndLeftAtIsNull(game.getId(), userId)) {
                throw new IllegalArgumentException("You are already in this game");
            }

            User user = userService.getUserById(userId);
            if (user == null) {
                throw new IllegalArgumentException("User not found");
            }

            // Add player to game
            Long gamePlayerId = IDGenerator.generateId();
            GamePlayer player = GamePlayer.builder()
                    .id(gamePlayerId)
                    .game(game)
                    .user(user)
                    .isHost(false)
                    .isReady(false)
                    .joinedAt(LocalDateTime.now())
                    .build();
            gamePlayerRepository.save(player);

            game.addPlayer();
            gameRepository.save(game);
            
            // Add player to Redis lobby
            addPlayerToRedisLobby(game.getId(), userId);

            // Send WebSocket notification to all players in the game
            try {
                liveGameOrchestrator.handlePlayerJoined(game, player);
                log.debug("LiveGameOrchestrator handled player join: gameId={}, userId={}", game.getId(), userId);
            } catch (Exception e) {
                log.warn("Failed to handle player join via LiveGameOrchestrator: gameId={}, userId={}, error={}", 
                        game.getId(), userId, e.getMessage());
                // Continue without failing the join operation
            }

            log.debug("Player joined event logged: gameId={}, userId={}", game.getId(), userId);

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Joined game successfully")
                    .data(mapToGraphQLGameLobby(game))
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for join game: {}", e.getMessage());
            return createErrorResponse(400, e.getMessage());
        } catch (Exception e) {
            log.error("Error joining game: {}", e.getMessage(), e);
            return createErrorResponse(500, "Failed to join game");
        }
    }

    @Override
    @Transactional
    public ApiResponse leaveGame(Long gameId, Long userId) {
        try {
            validateLeaveGameInput(gameId, userId);

            Game game = gameRepository.findOne(gameId);
            if (game == null) {
                throw new IllegalArgumentException("Game not found");
            }

            GamePlayer player = gamePlayerRepository.findByGameIdAndUserIdAndLeftAtIsNull(gameId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("You are not in this game"));

            player.leaveGame();
            gamePlayerRepository.save(player);

            game.removePlayer();
            
            // Remove player from Redis lobby and ready set
            removePlayerFromRedisLobby(game.getId(), userId);

            // Send WebSocket notification to remaining players
            try {
                liveGameOrchestrator.handlePlayerLeft(game, player);
                log.debug("LiveGameOrchestrator handled player leave: gameId={}, userId={}", game.getId(), userId);
            } catch (Exception e) {
                log.warn("Failed to handle player leave via LiveGameOrchestrator: gameId={}, userId={}, error={}", 
                        game.getId(), userId, e.getMessage());
                // Continue without failing the leave operation
            }
            
            // If host leaves, transfer host to another player or abandon game
            if (Boolean.TRUE.equals(player.getIsHost())) {
                List<GamePlayer> activePlayers = gamePlayerRepository.findByGameIdAndLeftAtIsNull(gameId);
                if (!activePlayers.isEmpty()) {
                    GamePlayer newHost = activePlayers.get(0);
                    newHost.setIsHost(true);
                    gamePlayerRepository.save(newHost);
                    game.setUser(newHost.getUser());
                } else {
                    game.setStatus(Game.GameStatus.ABANDONED);
                }
            }

            // If no players left, abandon game
            if (game.getCurrentPlayers() == 0) {
                game.setStatus(Game.GameStatus.ABANDONED);
                // Clear all Redis data when game is abandoned
                try {
                    gameStateRepository.clearAllGameData(game.getId());
                    log.debug("Cleared all Redis data for abandoned game {}", game.getId());
                } catch (Exception e) {
                    log.error("Failed to clear Redis data for abandoned game {}: {}", game.getId(), e.getMessage(), e);
                }
            }

            gameRepository.save(game);

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Left game successfully")
                    .data(Empty.newBuilder().ok(true).build())
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for leave game: {}", e.getMessage());
            return createErrorResponse(400, e.getMessage());
        } catch (Exception e) {
            log.error("Error leaving game: {}", e.getMessage(), e);
            return createErrorResponse(500, "Failed to leave game");
        }
    }

    @Override
    @Transactional
    public ApiResponse setPlayerReady(Long gameId, Long userId, Boolean ready) {
        try {
            validateSetPlayerReadyInput(gameId, userId, ready);

            GamePlayer player = gamePlayerRepository.findByGameIdAndUserIdAndLeftAtIsNull(gameId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("You are not in this game"));

            if (ready) {
                player.markReady();
            } else {
                player.markNotReady();
            }
            gamePlayerRepository.save(player);

            Game game = gameRepository.findOne(gameId);
            
            // Update player ready status in Redis
            updatePlayerReadyStatusInRedis(game, userId, ready);

            // Send WebSocket notification about player readiness change
            try {
                liveGameOrchestrator.handleRealtimeEvent(
                    gameId, 
                    "player.readiness.changed", 
                    Map.of(
                        "gameId", gameId,
                        "userId", userId,
                        "username", player.getUser().getUsername(),
                        "isReady", ready,
                        "lobbyUpdate", mapToGraphQLGameLobby(game)
                    )
                );
                log.debug("LiveGameOrchestrator handled player readiness change: gameId={}, userId={}, ready={}", 
                        gameId, userId, ready);
            } catch (Exception e) {
                log.warn("Failed to handle player readiness change via LiveGameOrchestrator: gameId={}, userId={}, error={}", 
                        gameId, userId, e.getMessage());
                // Continue without failing the ready status update
            }
            
            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Player ready status updated")
                    .data(mapToGraphQLGameLobby(game))
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for set player ready: {}", e.getMessage());
            return createErrorResponse(400, e.getMessage());
        } catch (Exception e) {
            log.error("Error setting player ready: {}", e.getMessage(), e);
            return createErrorResponse(500, "Failed to set player ready");
        }
    }


    @Override
    @Transactional
    public ApiResponse submitAnswerApiResponse(SubmitAnswerCmd cmd, Long userId) {
        try {
            validateSubmitAnswerInput(cmd, userId);

            com.mabawa.triviacrave.game.entity.Game game = gameRepository.findOne(cmd.getGameId());
            if (game == null) {
                throw new IllegalArgumentException("Game not found");
            }

            if (!game.getUser().getId().equals(userId)) {
                throw new IllegalArgumentException("You can only answer questions in your own game");
            }

            if (!game.isGameActive()) {
                throw new IllegalArgumentException("Game is not active");
            }

            // Find the game question
            com.mabawa.triviacrave.game.entity.GameQuestion gameQuestion = game.getGameQuestions().stream()
                    .filter(gq -> gq.getQuestion().getId().equals(cmd.getQuestionId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Question not found in this game"));

            if (gameQuestion.hasBeenAnswered()) {
                throw new IllegalArgumentException("Question has already been answered");
            }

            // Answer the question
            gameQuestion.answerQuestion(cmd.getSelectedAnswer(), cmd.getTimeSpent());
            gameQuestionRepository.save(gameQuestion);

            // Calculate score with bonuses
            int baseScore = scoreService.calculateBaseScore(
                    Difficulty.valueOf(gameQuestion.getDifficulty().name()),
                    gameQuestion.isAnsweredCorrectly()
            );

            int timeBonus = scoreService.calculateTimeBonus(cmd.getTimeSpent(), 60); // Assuming 60 seconds default
            
            // Calculate streak bonus
            int currentStreak = calculateCurrentStreak(game);
            int streakBonus = scoreService.calculateStreakBonus(currentStreak);

            int totalQuestionScore = baseScore + timeBonus + streakBonus;
            gameQuestion.setPointsEarned(totalQuestionScore);
            gameQuestionRepository.save(gameQuestion);

            // Update game statistics
            game.answerQuestion(gameQuestion.isAnsweredCorrectly(), totalQuestionScore);
            gameRepository.save(game);

            // Update Redis game state and player score
            updateRedisGameStateForAnswer(game, userId, totalQuestionScore);

            // Handle real-time answer submission through LiveGameOrchestrator
            try {
                liveGameOrchestrator.handleAnswerSubmission(
                    game.getId(), 
                    userId, 
                    cmd.getSelectedAnswer(), 
                    cmd.getTimeSpent(), 
                    cmd.getQuestionId()
                );
                log.debug("Answer submission handled by LiveGameOrchestrator: gameId={}, userId={}", game.getId(), userId);
            } catch (Exception e) {
                log.warn("Failed to handle answer submission via LiveGameOrchestrator: gameId={}, userId={}, error={}", 
                        game.getId(), userId, e.getMessage());
                // Continue with fallback WebSocket notification
            }

            // Note: Answer results are already handled by liveGameOrchestrator.handleAnswerSubmission() above
            // Removed duplicate WebSocket notification to avoid double messaging

            log.debug("Answer submitted and processed: gameId={}, userId={}", game.getId(), userId);

            // Check if game is complete
            boolean gameComplete = isGameComplete(game);
            if (gameComplete) {
                completeGame(game);
            }

            com.mabawa.triviacrave.generated.graphql.types.GameQuestionResult result = 
                    com.mabawa.triviacrave.generated.graphql.types.GameQuestionResult.newBuilder()
                    .gameQuestion(mapToGraphQLGameQuestion(gameQuestion))
                    .isCorrect(gameQuestion.isAnsweredCorrectly())
                    .correctAnswer(gameQuestion.getCorrectAnswer())
                    .pointsAwarded(totalQuestionScore)
                    .explanation(gameQuestion.getQuestion().getExplanation())
                    .timeSpent(gameQuestion.getTimeSpent() != null ? gameQuestion.getTimeSpent() : 0)
                    .build();

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Answer submitted successfully")
                    .data(result)
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for submit answer: {}", e.getMessage());
            return ApiResponse.newBuilder()
                    .status(400)
                    .message(e.getMessage())
                    .data(Empty.newBuilder().ok(false).build())
                    .build();
        } catch (Exception e) {
            log.error("Error submitting answer: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to submit answer")
                    .data(Empty.newBuilder().ok(false).build())
                    .build();
        }
    }

    @Override
    @Transactional
    public ApiResponse endGameApiResponse(EndGameCmd cmd, Long userId) {
        try {
            if (cmd.getGameId() <= 0) {
                throw new IllegalArgumentException("Game ID is required");
            }

            com.mabawa.triviacrave.game.entity.Game game = gameRepository.findOne(cmd.getGameId());
            if (game == null) {
                throw new IllegalArgumentException("Game not found");
            }

            if (!game.getUser().getId().equals(userId)) {
                throw new IllegalArgumentException("You can only end your own game");
            }

            if (!game.isGameActive()) {
                throw new IllegalArgumentException("Game is already finished");
            }

            // Determine end reason for live game orchestration
            GameEndReason endReason;
            if (cmd.getReason() != null && cmd.getReason().toLowerCase().contains("abandon")) {
                game.abandonGame();
                endReason = GameEndReason.ABANDONED;
            } else {
                game.completeGame();
                endReason = GameEndReason.COMPLETED;
            }

            // End live game orchestration for real-time features
            try {
                liveGameOrchestrator.endLiveGame(game.getId(), endReason);
                log.debug("Live game orchestration ended via endGame API: gameId={}, reason={}", game.getId(), endReason);
            } catch (Exception e) {
                log.warn("Failed to end live game orchestration via endGame API: gameId={}, error={}", 
                        game.getId(), e.getMessage());
                // Continue with standard game end operation - basic functionality remains intact
            }

            // Handle final score calculation based on game type
            if (game.getMaxPlayers() == 1) {
                // Single player - calculate final score with completion bonuses
                calculateSinglePlayerFinalScore(game);
                gameRepository.save(game);
                log.debug("Final score calculated for single-player game: gameId={}, finalScore={}", 
                         game.getId(), game.getScore());
            } else {
                // Multiplayer - LiveGameOrchestrator already handles final scores correctly
                // in liveGameOrchestrator.endLiveGame() called at line 510
                // Just update game status, don't recalculate scores to avoid conflicts
                gameRepository.save(game);
                log.debug("Final scores handled by LiveGameOrchestrator for multiplayer game: gameId={}, maxPlayers={}", 
                         game.getId(), game.getMaxPlayers());
            }

            // Note: Game completion is already handled by liveGameOrchestrator.endLiveGame() above
            // Removed duplicate WebSocket notification to avoid double messaging

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Game ended successfully")
                    .data(mapToGraphQLGame(game))
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for end game: {}", e.getMessage());
            return ApiResponse.newBuilder()
                    .status(400)
                    .message(e.getMessage())
                    .data(Empty.newBuilder().ok(false).build())
                    .build();
        } catch (Exception e) {
            log.error("Error ending game: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to end game")
                    .data(Empty.newBuilder().ok(false).build())
                    .build();
        }
    }

    public ApiResponse getActiveGameApiResponse(Long userId) {
        try {
            if (userId == null) {
                throw new IllegalArgumentException("User ID is required");
            }

            List<com.mabawa.triviacrave.game.entity.Game> activeGames = gameRepository.findActiveGamesByUserId(userId);
            
            if (activeGames.isEmpty()) {
                return ApiResponse.newBuilder()
                        .status(200)
                        .message("No active game found")
                        .data(null)
                        .build();
            }

            com.mabawa.triviacrave.game.entity.Game activeGame = activeGames.get(0); // Should only be one active game

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Active game retrieved successfully")
                    .data(mapToGraphQLGame(activeGame))
                    .build();

        } catch (Exception e) {
            log.error("Error retrieving active game: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to retrieve active game")
                    .data(null)
                    .build();
        }
    }

    public ApiResponse getActiveGameStatusApiResponse(Long userId) {
        try {
            if (userId == null) {
                throw new IllegalArgumentException("User ID is required");
            }

            List<com.mabawa.triviacrave.game.entity.Game> activeGames = gameRepository.findActiveGamesByUserId(userId);
            boolean hasActiveGame = !activeGames.isEmpty();

            ActiveGameStatus status = ActiveGameStatus.newBuilder()
                    .hasActiveGame(hasActiveGame)
                    .activeGame(hasActiveGame ? mapToGraphQLGame(activeGames.get(0)) : null)
                    .build();

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Active game status retrieved successfully")
                    .data(status)
                    .build();

        } catch (Exception e) {
            log.error("Error retrieving active game status: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to retrieve active game status")
                    .data(ActiveGameStatus.newBuilder().hasActiveGame(false).build())
                    .build();
        }
    }

    public ApiResponse getGameApiResponse(Long gameId) {
        try {
            if (gameId == null) {
                throw new IllegalArgumentException("Game ID is required");
            }

            com.mabawa.triviacrave.game.entity.Game game = gameRepository.findOne(gameId);
            if (game == null) {
                throw new IllegalArgumentException("Game not found");
            }

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Game retrieved successfully")
                    .data(mapToGraphQLGame(game))
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for get game: {}", e.getMessage());
            return ApiResponse.newBuilder()
                    .status(400)
                    .message(e.getMessage())
                    .data(null)
                    .build();
        } catch (Exception e) {
            log.error("Error retrieving game: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to retrieve game")
                    .data(null)
                    .build();
        }
    }

    public ApiResponse getGameHistoryApiResponse(Long userId, Integer limit, Integer offset) {
        try {
            if (userId == null) {
                throw new IllegalArgumentException("User ID is required");
            }

            List<com.mabawa.triviacrave.game.entity.Game> games = gameRepository.findGamesByUserId(userId);
            
            // Apply pagination
            if (offset != null && offset > 0) {
                games = games.stream().skip(offset).toList();
            }
            if (limit != null && limit > 0) {
                games = games.stream().limit(limit).toList();
            }

            List<com.mabawa.triviacrave.generated.graphql.types.Game> graphqlGames = 
                    games.stream()
                            .map(this::mapToGraphQLGame)
                            .toList();

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Game history retrieved successfully")
                    .data(GameList.newBuilder().games(graphqlGames).build())
                    .build();

        } catch (Exception e) {
            log.error("Error retrieving game history: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to retrieve game history")
                    .data(GameList.newBuilder().games(List.of()).build())
                    .build();
        }
    }

    public ApiResponse getGamesByModeApiResponse(GameMode mode, Integer limit, Integer offset) {
        try {
            if (mode == null) {
                throw new IllegalArgumentException("Game mode is required");
            }

            List<com.mabawa.triviacrave.game.entity.Game> games = gameRepository.findGamesByMode(mapToEntityGameMode(mode));
            
            // Apply pagination
            if (offset != null && offset > 0) {
                games = games.stream().skip(offset).toList();
            }
            if (limit != null && limit > 0) {
                games = games.stream().limit(limit).toList();
            }

            List<com.mabawa.triviacrave.generated.graphql.types.Game> graphqlGames = 
                    games.stream()
                            .map(this::mapToGraphQLGame)
                            .toList();

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Games by mode retrieved successfully")
                    .data(GameList.newBuilder().games(graphqlGames).build())
                    .build();

        } catch (Exception e) {
            log.error("Error retrieving games by mode: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to retrieve games by mode")
                    .data(GameList.newBuilder().games(List.of()).build())
                    .build();
        }
    }

    @Override
    @Transactional
    public ApiResponse pauseGame(Long gameId, Long userId) {
        try {
            Game game = validateGameOwnership(gameId, userId);
            Game.GameStatus previousStatus = game.getStatus();
            game.pauseGame();
            gameRepository.save(game);

            // Send WebSocket notification for game state change
            try {
                liveGameOrchestrator.handleGameStateChange(game, previousStatus);
                log.debug("LiveGameOrchestrator handled game pause: gameId={}", gameId);
            } catch (Exception e) {
                log.warn("Failed to handle game pause via LiveGameOrchestrator: gameId={}, error={}", 
                        gameId, e.getMessage());
                // Continue without failing the pause operation
            }

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Game paused successfully")
                    .data(mapToGraphQLGame(game))
                    .build();

        } catch (Exception e) {
            log.error("Error pausing game: {}", e.getMessage(), e);
            return createErrorResponse("Failed to pause game");
        }
    }

    @Override
    @Transactional
    public ApiResponse resumeGame(Long gameId, Long userId) {
        try {
            Game game = validateGameOwnership(gameId, userId);
            Game.GameStatus previousStatus = game.getStatus();
            game.resumeGame();
            gameRepository.save(game);

            // Send WebSocket notification for game state change
            try {
                liveGameOrchestrator.handleGameStateChange(game, previousStatus);
                log.debug("LiveGameOrchestrator handled game resume: gameId={}", gameId);
            } catch (Exception e) {
                log.warn("Failed to handle game resume via LiveGameOrchestrator: gameId={}, error={}", 
                        gameId, e.getMessage());
                // Continue without failing the resume operation
            }

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Game resumed successfully")
                    .data(mapToGraphQLGame(game))
                    .build();

        } catch (Exception e) {
            log.error("Error resuming game: {}", e.getMessage(), e);
            return createErrorResponse("Failed to resume game");
        }
    }

    @Override
    @Transactional
    public ApiResponse abandonGame(Long gameId, Long userId) {
        try {
            Game game = validateGameOwnership(gameId, userId);
            Game.GameStatus previousStatus = game.getStatus();
            game.abandonGame();
            gameRepository.save(game);

            // End live game orchestration for real-time features
            try {
                liveGameOrchestrator.endLiveGame(gameId, GameEndReason.ABANDONED);
                log.debug("Live game orchestration ended for abandoned game: gameId={}", gameId);
            } catch (Exception e) {
                log.warn("Failed to end live game orchestration for abandoned game: gameId={}, error={}", 
                        gameId, e.getMessage());
                // Continue with standard abandon operation - basic functionality remains intact
            }

            // Send WebSocket notification for game state change
            try {
                liveGameOrchestrator.handleGameStateChange(game, previousStatus);
                log.debug("LiveGameOrchestrator handled game abandon: gameId={}", gameId);
            } catch (Exception e) {
                log.warn("Failed to handle game abandon via LiveGameOrchestrator: gameId={}, error={}", 
                        gameId, e.getMessage());
                // Continue without failing the abandon operation
            }

            // Clear Redis data when game is abandoned
            try {
                gameStateRepository.clearAllGameData(gameId);
                log.debug("Cleared Redis data for abandoned game {}", gameId);
            } catch (Exception e) {
                log.error("Failed to clear Redis data for abandoned game {}: {}", gameId, e.getMessage(), e);
            }

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Game abandoned successfully")
                    .data(mapToGraphQLGame(game))
                    .build();

        } catch (Exception e) {
            log.error("Error abandoning game: {}", e.getMessage(), e);
            return createErrorResponse("Failed to abandon game");
        }
    }

    // Helper methods


    private void validateSubmitAnswerInput(SubmitAnswerCmd cmd, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (cmd.getGameId() <= 0) {
            throw new IllegalArgumentException("Game ID is required");
        }
        if (cmd.getQuestionId() <= 0) {
            throw new IllegalArgumentException("Question ID is required");
        }
        if (cmd.getSelectedAnswer() == null || cmd.getSelectedAnswer().trim().isEmpty()) {
            throw new IllegalArgumentException("Selected answer is required");
        }
        if (cmd.getTimeSpent() < 0) {
            throw new IllegalArgumentException("Time spent must be non-negative");
        }
    }

    private com.mabawa.triviacrave.game.entity.Game validateGameOwnership(Long gameId, Long userId) {
        if (gameId == null) {
            throw new IllegalArgumentException("Game ID is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        com.mabawa.triviacrave.game.entity.Game game = gameRepository.findOne(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Game not found");
        }

        if (!game.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("You can only modify your own games");
        }

        return game;
    }


    private int calculateCurrentStreak(com.mabawa.triviacrave.game.entity.Game game) {
        List<com.mabawa.triviacrave.game.entity.GameQuestion> answeredQuestions = game.getGameQuestions().stream()
                .filter(com.mabawa.triviacrave.game.entity.GameQuestion::hasBeenAnswered)
                .sorted((a, b) -> a.getQuestionOrder().compareTo(b.getQuestionOrder()))
                .toList();

        int streak = 0;
        for (int i = answeredQuestions.size() - 1; i >= 0; i--) {
            if (answeredQuestions.get(i).isAnsweredCorrectly()) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    private boolean isGameComplete(com.mabawa.triviacrave.game.entity.Game game) {
        long totalQuestions = game.getGameQuestions().size();
        long answeredQuestions = game.getGameQuestions().stream()
                .mapToLong(gq -> gq.hasBeenAnswered() ? 1 : 0)
                .sum();
        return answeredQuestions >= totalQuestions;
    }

    private void completeGame(com.mabawa.triviacrave.game.entity.Game game) {
        game.completeGame();
        gameRepository.save(game);
        
        // End live game orchestration for real-time features
        try {
            liveGameOrchestrator.endLiveGame(game.getId(), GameEndReason.COMPLETED);
            log.debug("Live game orchestration ended: gameId={}", game.getId());
        } catch (Exception e) {
            log.warn("Failed to end live game orchestration: gameId={}, error={}", 
                    game.getId(), e.getMessage());
            // Continue with standard game completion - basic functionality remains intact
        }
        
        // Note: Game completion is already handled by liveGameOrchestrator.endLiveGame() above
        // Removed duplicate WebSocket notification to avoid double messaging
        
        // Update Redis state for game completion and cleanup
        finalizeGameInRedis(game);
    }

    private com.mabawa.triviacrave.generated.graphql.types.GameQuestion getNextQuestion(com.mabawa.triviacrave.game.entity.Game game) {
        return game.getGameQuestions().stream()
                .filter(gq -> !gq.hasBeenAnswered())
                .sorted((a, b) -> a.getQuestionOrder().compareTo(b.getQuestionOrder()))
                .findFirst()
                .map(this::mapToGraphQLGameQuestion)
                .orElse(null);
    }

    private com.mabawa.triviacrave.game.entity.Game.GameMode mapToEntityGameMode(GameMode graphqlMode) {
        return switch (graphqlMode) {
            case MULTIPLAYER -> Game.GameMode.TIMED_CHALLENGE;
            case PRACTICE -> Game.GameMode.CUSTOM;
            case TOURNAMENT -> Game.GameMode.SURVIVAL;
            default -> Game.GameMode.QUICK_PLAY;
        };
    }

    private GameMode mapFromEntityGameMode(com.mabawa.triviacrave.game.entity.Game.GameMode entityMode) {
        switch (entityMode) {
            case QUICK_PLAY:
                return GameMode.SINGLE_PLAYER;
            case TIMED_CHALLENGE:
                return GameMode.MULTIPLAYER;
            case CUSTOM:
                return GameMode.PRACTICE;
            case SURVIVAL:
                return GameMode.TOURNAMENT;
            default:
                return GameMode.SINGLE_PLAYER;
        }
    }

    private GameStatus mapFromEntityGameStatus(com.mabawa.triviacrave.game.entity.Game.GameStatus entityStatus) {
        return switch (entityStatus) {
            case WAITING -> GameStatus.WAITING;
            case COMPLETED -> GameStatus.COMPLETED;
            case ABANDONED -> GameStatus.ABANDONED;
            case PAUSED -> GameStatus.PAUSED;
            default -> GameStatus.ACTIVE;
        };
    }

    private com.mabawa.triviacrave.generated.graphql.types.Game mapToGraphQLGame(com.mabawa.triviacrave.game.entity.Game game) {
        List<Long> categoryIds = game.getCategory() != null ? 
                List.of(game.getCategory().getId()) : List.of();

        return com.mabawa.triviacrave.generated.graphql.types.Game.newBuilder()
                .id(game.getId())
                .userId(game.getUser().getId())
                .mode(mapFromEntityGameMode(game.getGameMode()))
                .status(mapFromEntityGameStatus(game.getStatus()))
                .categoryIds(categoryIds)
                .totalQuestions(game.getGameQuestions().size())
                .currentQuestionIndex(game.getQuestionsAnswered())
                .score(game.getScore())
                .correctAnswers(game.getCorrectAnswers())
                .incorrectAnswers(game.getQuestionsAnswered() - game.getCorrectAnswers())
                .timeSpent(game.getDurationSeconds() != null ? game.getDurationSeconds().longValue() : 0L)
                .maxPlayers(game.getMaxPlayers())
                .currentPlayers(game.getCurrentPlayers())
                .inviteCode(game.getInviteCode())
                .createdAt(game.getCreatedAt())
                .startedAt(game.getStartedAt())
                .endedAt(game.getCompletedAt())
                .questions(game.getGameQuestions().stream()
                        .map(this::mapToGraphQLGameQuestion)
                        .toList())
                .currentQuestion(getNextQuestion(game))
                .build();
    }

    private com.mabawa.triviacrave.generated.graphql.types.GameQuestion mapToGraphQLGameQuestion(com.mabawa.triviacrave.game.entity.GameQuestion gameQuestion) {
        return com.mabawa.triviacrave.generated.graphql.types.GameQuestion.newBuilder()
                .id(gameQuestion.getId())
                .gameId(gameQuestion.getGame().getId())
                .selectedAnswer(gameQuestion.getUserAnswer())
                .correctAnswer(gameQuestion.getCorrectAnswer())
                .isCorrect(gameQuestion.isAnsweredCorrectly())
                .timeSpent(gameQuestion.getTimeTakenSeconds())
                .answeredAt(gameQuestion.getAnsweredAt())
                .pointsAwarded(gameQuestion.getPointsEarned())
                .build();
    }

    private ApiResponse createErrorResponse(String message) {
        return ApiResponse.newBuilder()
                .status(500)
                .message(message)
                .data(Empty.newBuilder().ok(false).build())
                .build();
    }
    // Interface methods that return direct types for GraphQL queries
    
    @Override
    @Transactional
    public ApiResponse startGame(StartGameCmd cmd) {
        try {
            Long userId = getCurrentUserId();
            validateNewStartGameInput(cmd, userId);

            Game game = gameRepository.findOne(cmd.getGameId());
            if (game == null) {
                throw new IllegalArgumentException("Game not found");
            }

            // Check if user is host
            GamePlayer hostPlayer = gamePlayerRepository.findByGameIdAndIsHostTrueAndLeftAtIsNull(game.getId())
                    .orElseThrow(() -> new IllegalArgumentException("No host found for this game"));
            
            if (!hostPlayer.getUser().getId().equals(userId)) {
                throw new IllegalArgumentException("Only the host can start the game");
            }

            if (!game.isGameWaiting()) {
                throw new IllegalArgumentException("Game cannot be started in current state");
            }

            // Check if all players are ready (for multiplayer games)
            if (game.getMaxPlayers() > 1) {
                List<GamePlayer> readyPlayers = gamePlayerRepository.findByGameIdAndIsReadyTrueAndLeftAtIsNull(game.getId());
                long activePlayers = gamePlayerRepository.countByGameIdAndLeftAtIsNull(game.getId());
                
                if (readyPlayers.size() < activePlayers) {
                    throw new IllegalArgumentException("Not all players are ready");
                }
            }

            return self.startGameInternal(game);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for start game: {}", e.getMessage());
            return createErrorResponse(400, e.getMessage());
        } catch (Exception e) {
            log.error("Error starting game: {}", e.getMessage(), e);
            return createErrorResponse(500, "Failed to start game");
        }
    }
    
    @Override
    public ApiResponse submitAnswer(SubmitAnswerCmd cmd) {
        // Get current user from security context
        Long userId = getCurrentUserId();
        return self.submitAnswerApiResponse(cmd, userId);
    }
    
    @Override
    public ApiResponse endGame(EndGameCmd cmd) {
        // Get current user from security context
        Long userId = getCurrentUserId();
        return self.endGameApiResponse(cmd, userId);
    }
    
    @Override
    public com.mabawa.triviacrave.generated.graphql.types.Game getActiveGame() {
        // Get current user from security context
        Long userId = getCurrentUserId();
        
        List<Game> activeGames = gameRepository.findByUserIdAndStatus(userId, Game.GameStatus.IN_PROGRESS);
        if (activeGames.isEmpty()) {
            return null; // No active game
        }
        
        Game activeGame = activeGames.get(0); // Should only be one active game per user
        return mapToGraphQLGame(activeGame);
    }
    
    @Override
    public ActiveGameStatus getActiveGameStatus() {
        // Get current user from security context
        Long userId = getCurrentUserId();
        
        List<Game> activeGames = gameRepository.findByUserIdAndStatus(userId, Game.GameStatus.IN_PROGRESS);
        boolean hasActiveGame = !activeGames.isEmpty();
        
        return ActiveGameStatus.newBuilder()
                .hasActiveGame(hasActiveGame)
                .activeGame(hasActiveGame ? mapToGraphQLGame(activeGames.get(0)) : null)
                .build();
    }
    
    @Override
    public com.mabawa.triviacrave.generated.graphql.types.Game getGame(Long gameId) {
        if (gameId == null || gameId <= 0) {
            throw new RuntimeException("Game ID is required");
        }
        
        Game game = gameRepository.findOne(gameId);
        if (game == null) {
            return null;
        }
        
        return mapToGraphQLGame(game);
    }
    
    @Override
    public java.util.List<com.mabawa.triviacrave.generated.graphql.types.Game> getGameHistory(Long userId, Integer limit, Integer offset) {
        if (userId == null || userId <= 0) {
            throw new RuntimeException("User ID is required");
        }
        
        List<Game> games = gameRepository.findByUserId(userId).stream()
                .sorted((a, b) -> b.getStartedAt().compareTo(a.getStartedAt())) // Most recent first
                .toList();
        
        // Apply pagination
        if (offset != null && offset > 0) {
            games = games.stream().skip(offset).toList();
        }
        
        if (limit != null && limit > 0) {
            games = games.stream().limit(limit).toList();
        }
        
        return games.stream()
                .map(this::mapToGraphQLGame)
                .toList();
    }
    
    @Override
    public java.util.List<com.mabawa.triviacrave.generated.graphql.types.Game> getGameHistory(Integer limit, Integer offset) {
        // Extract userId from security context
        Long userId = getCurrentUserId();
        return getGameHistory(userId, limit, offset);
    }
    
    @Override
    public GameLobby getGameLobby(Long gameId) {
        if (gameId == null || gameId <= 0) {
            throw new RuntimeException("Game ID is required");
        }
        
        Game game = gameRepository.findOne(gameId);
        if (game == null) {
            return null;
        }
        
        return mapToGraphQLGameLobby(game);
    }
    
    @Override
    public java.util.List<com.mabawa.triviacrave.generated.graphql.types.Game> getOpenGames(GameMode mode, Integer limit) {
        List<Game> games = gameRepository.findByStatus(Game.GameStatus.WAITING).stream()
                .filter(g -> g.getInviteCode() == null) // Only public games
                .filter(g -> mode == null || mapFromEntityGameMode(g.getGameMode()).equals(mode))
                .filter(g -> !g.isFull())
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
        
        if (limit != null && limit > 0) {
            games = games.stream().limit(limit).toList();
        }
        
        return games.stream()
                .map(this::mapToGraphQLGame)
                .toList();
    }

    @Override
    public java.util.List<com.mabawa.triviacrave.generated.graphql.types.Game> getGamesByMode(GameMode mode, Integer limit, Integer offset) {
        if (mode == null) {
            throw new RuntimeException("Game mode is required");
        }
        
        List<Game> games = gameRepository.findByMode(mapFromGraphQLGameMode(mode)).stream()
                .sorted((a, b) -> b.getStartedAt().compareTo(a.getStartedAt())) // Most recent first
                .toList();
        
        // Apply pagination
        if (offset != null && offset > 0) {
            games = games.stream().skip(offset).toList();
        }
        
        if (limit != null && limit > 0) {
            games = games.stream().limit(limit).toList();
        }
        
        return games.stream()
                .map(this::mapToGraphQLGame)
                .toList();
    }
    
    private Long getCurrentUserId() {
        // Get current user from security context
        // This might need to be adjusted based on your authentication implementation
        try {
            org.springframework.security.core.Authentication auth = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getPrincipal() == null) {
                throw new RuntimeException("User not authenticated");
            }
            return Long.valueOf(auth.getName());
        } catch (Exception e) {
            throw new RuntimeException("Unable to extract user ID from authentication context: " + e.getMessage());
        }
    }
    
    private Game.GameMode mapFromGraphQLGameMode(GameMode mode) {
        // Convert GraphQL GameMode to entity GameMode
        return Game.GameMode.valueOf(mode.name());
    }

    // New helper methods for multiplayer functionality

    private void validateCreateGameInput(CreateGameCmd cmd, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (cmd.getMode() == null) {
            throw new IllegalArgumentException("Game mode is required");
        }
        if (cmd.getMaxPlayers() != null && cmd.getMaxPlayers() < 1) {
            throw new IllegalArgumentException("Max players must be at least 1");
        }
        if (cmd.getMaxPlayers() != null && cmd.getMaxPlayers() > 10) {
            throw new IllegalArgumentException("Max players cannot exceed 10");
        }
    }

    private void validateJoinGameInput(JoinGameCmd cmd, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (cmd.getGameId() == null && (cmd.getInviteCode() == null || cmd.getInviteCode().trim().isEmpty())) {
            throw new IllegalArgumentException("Either game ID or invite code is required");
        }
    }

    private void validateLeaveGameInput(Long gameId, Long userId) {
        if (gameId == null) {
            throw new IllegalArgumentException("Game ID is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
    }

    private void validateSetPlayerReadyInput(Long gameId, Long userId, Boolean ready) {
        if (gameId == null) {
            throw new IllegalArgumentException("Game ID is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (ready == null) {
            throw new IllegalArgumentException("Ready status is required");
        }
    }

    private void validateNewStartGameInput(StartGameCmd cmd, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (cmd.getGameId() <= 0) {
            throw new IllegalArgumentException("Game ID is required");
        }
    }

    private Game findGameByIdOrInviteCode(Long gameId, String inviteCode) {
        if (gameId != null) {
            return gameRepository.findOne(gameId);
        } else if (inviteCode != null && !inviteCode.trim().isEmpty()) {
            return gameRepository.findByInviteCode(inviteCode);
        }
        return null;
    }

    private String generateInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        
        for (int i = 0; i < 8; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        // Ensure uniqueness (simple retry approach)
        if (gameRepository.findByInviteCode(code.toString()) != null) {
            return generateInviteCode(); // Recursive retry
        }
        
        return code.toString();
    }

    @Override
    @Transactional
    public ApiResponse startGameInternal(Game game) {
        try {
            // Start the game
            game.startGame();
            gameRepository.save(game);

            // Generate questions for the game
            List<Question> questions = selectQuestionsForGameInternal(game);
            if (questions.isEmpty()) {
                throw new IllegalArgumentException("No questions available for the specified criteria");
            }

            // Create GameQuestions
            List<GameQuestion> gameQuestions = new ArrayList<>();
            for (int i = 0; i < questions.size(); i++) {
                Long gameQuestionId = IDGenerator.generateId();
                GameQuestion gameQuestion = GameQuestion.builder()
                        .id(gameQuestionId)
                        .game(game)
                        .question(questions.get(i))
                        .questionOrder(i + 1)
                        .isCorrect(false)
                        .pointsEarned(0)
                        .createdAt(LocalDateTime.now())
                        .build();
                gameQuestions.add(gameQuestion);
                gameQuestionRepository.save(gameQuestion);
            }

            game.setGameQuestions(gameQuestions);
            gameRepository.save(game);

            // Update Redis game state when game starts
            initializeRedisGameStateForStart(game, gameQuestions);

            // Start live game orchestration for real-time features
            try {
                liveGameOrchestrator.startLiveGame(game.getId());
                log.debug("Live game orchestration started: gameId={}", game.getId());
            } catch (Exception e) {
                log.warn("Failed to start live game orchestration: gameId={}, error={}", 
                        game.getId(), e.getMessage());
                // Continue without failing the game start - basic functionality remains intact
            }

            // Note: Game start is already handled by liveGameOrchestrator.startLiveGame() above
            // Removed duplicate WebSocket notification to avoid double messaging

            log.debug("Game started and initialized: game {}", game.getId());

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Game started successfully")
                    .data(mapToGraphQLGame(game))
                    .build();

        } catch (Exception e) {
            log.error("Error in startGameInternal: {}", e.getMessage(), e);
            throw e;
        }
    }

    private List<Question> selectQuestionsForGameInternal(Game game) {
        List<Question> availableQuestions;

        // Get questions from category if specified
        if (game.getCategory() != null) {
            availableQuestions = categoryService.getCategoryActiveQuestions(game.getCategory().getId());
        } else {
            availableQuestions = questionService.getAllActiveQuestions();
        }

        // Determine question count based on game mode
        int questionCount = game.getGameMode().getDefaultQuestionCount();

        // Randomly select questions
        Collections.shuffle(availableQuestions);
        return availableQuestions.stream()
                .limit(questionCount)
                .toList();
    }

    private GameLobby mapToGraphQLGameLobby(Game game) {
        List<GamePlayer> players = gamePlayerRepository.findByGameIdAndLeftAtIsNull(game.getId());
        
        boolean canStart = game.canStart() && 
                          (game.getMaxPlayers() == 1 || 
                           players.stream().allMatch(GamePlayer::getIsReady));
        
        boolean allReady = players.stream().allMatch(GamePlayer::getIsReady);

        return GameLobby.newBuilder()
                .game(mapToGraphQLGame(game))
                .players(players.stream().map(this::mapToGraphQLGamePlayer).toList())
                .canStart(canStart)
                .allPlayersReady(allReady)
                .build();
    }

    private com.mabawa.triviacrave.generated.graphql.types.GamePlayer mapToGraphQLGamePlayer(GamePlayer player) {
        return com.mabawa.triviacrave.generated.graphql.types.GamePlayer.newBuilder()
                .id(player.getId())
                .gameId(player.getGame().getId())
                .userId(player.getUser().getId())
                .username(player.getUser().getUsername())
                .isHost(player.getIsHost())
                .isReady(player.getIsReady())
                .joinedAt(player.getJoinedAt())
                .build();
    }


    private ApiResponse createErrorResponse(int status, String message) {
        return ApiResponse.newBuilder()
                .status(status)
                .message(message)
                .data(Empty.newBuilder().ok(false).build())
                .build();
    }

    // Redis Helper Methods

    /**
     * Initialize Redis game state and lobby for multiplayer games
     */
    private void initializeRedisGameState(Game game, Long userId) {
        try {
            if (game.getMaxPlayers() > 1) {
                // Initialize game state in Redis
                GameStateRepository.GameState gameState = new GameStateRepository.GameState();
                gameState.setGameId(game.getId());
                gameState.setStatus("WAITING");
                gameState.setCurrentQuestionIndex(0);
                gameState.setTotalQuestions(0); // Will be set when game starts
                gameStateRepository.saveGameState(game.getId(), gameState);
                
                // Add host to lobby
                gameStateRepository.addPlayerToLobby(game.getId(), userId.toString());
                log.debug("Initialized Redis state for multiplayer game {}", game.getId());
            }
        } catch (Exception e) {
            log.error("Failed to initialize Redis state for game {}: {}", game.getId(), e.getMessage(), e);
            // Continue without Redis - fallback to database only
        }
    }

    /**
     * Update player ready status in Redis with proper error handling
     */
    private void updatePlayerReadyStatusInRedis(Game game, Long userId, Boolean ready) {
        try {
            if (game.getMaxPlayers() > 1) {
                gameStateRepository.setPlayerReady(game.getId(), userId.toString(), ready);
                log.debug("Set player {} ready status to {} in Redis for game {}", userId, ready, game.getId());
            }
        } catch (Exception e) {
            log.error("Failed to set player {} ready status in Redis for game {}: {}", userId, game.getId(), e.getMessage(), e);
            // Continue without Redis - database state will be correct
        }
    }

    /**
     * Add player to Redis lobby with proper error handling
     */
    private void addPlayerToRedisLobby(Long gameId, Long userId) {
        try {
            gameStateRepository.addPlayerToLobby(gameId, userId.toString());
            // Set player as not ready initially
            gameStateRepository.setPlayerReady(gameId, userId.toString(), false);
            log.debug("Added player {} to Redis lobby for game {}", userId, gameId);
        } catch (Exception e) {
            log.error("Failed to add player {} to Redis lobby for game {}: {}", userId, gameId, e.getMessage(), e);
            // Continue without Redis - fallback to database only
        }
    }

    /**
     * Remove player from Redis lobby with proper error handling
     */
    private void removePlayerFromRedisLobby(Long gameId, Long userId) {
        try {
            gameStateRepository.removePlayerFromLobby(gameId, userId.toString());
            gameStateRepository.setPlayerReady(gameId, userId.toString(), false);
            log.debug("Removed player {} from Redis lobby for game {}", userId, gameId);
        } catch (Exception e) {
            log.error("Failed to remove player {} from Redis lobby for game {}: {}", userId, gameId, e.getMessage(), e);
            // Continue without Redis - database state will be correct
        }
    }

    /**
     * Update Redis game state for answer submission with comprehensive error handling
     */
    private void updateRedisGameStateForAnswer(Game game, Long userId, int totalQuestionScore) {
        try {
            GameStateRepository.GameState gameState = gameStateRepository.getGameState(game.getId());
            if (gameState != null) {
                // Update player score in Redis
                gameState.addPlayerScore(userId.toString(), totalQuestionScore);
                
                // Update current question index
                gameState.setCurrentQuestionIndex(game.getQuestionsAnswered());
                
                // Set next question if available
                List<GameQuestion> unansweredQuestions = game.getGameQuestions().stream()
                        .filter(gq -> !gq.hasBeenAnswered())
                        .sorted((a, b) -> a.getQuestionOrder().compareTo(b.getQuestionOrder()))
                        .toList();
                
                if (!unansweredQuestions.isEmpty()) {
                    gameState.setCurrentQuestionId(unansweredQuestions.getFirst().getQuestion().getId());
                    gameState.setQuestionStartTime(LocalDateTime.now());
                } else {
                    gameState.setCurrentQuestionId(null);
                    gameState.setQuestionStartTime(null);
                }
                
                gameState.updateActivity();
                gameStateRepository.saveGameState(game.getId(), gameState);
                log.debug("Updated Redis game state for answer submission in game {}", game.getId());
            }
        } catch (Exception e) {
            log.error("Failed to update Redis game state for answer submission in game {}: {}", game.getId(), e.getMessage(), e);
            // Continue without Redis - database state is consistent
        }
    }

    /**
     * Initialize Redis game state when game starts with all players and questions
     */
    private void initializeRedisGameStateForStart(Game game, List<GameQuestion> gameQuestions) {
        try {
            GameStateRepository.GameState gameState = gameStateRepository.getGameState(game.getId());
            if (gameState == null) {
                // Create new game state if not exists (for single player or fallback)
                gameState = new GameStateRepository.GameState();
                gameState.setGameId(game.getId());
            }
            gameState.setStatus("IN_PROGRESS");
            gameState.setCurrentQuestionIndex(0);
            gameState.setTotalQuestions(gameQuestions.size());
            gameState.setGameStartTime(game.getStartedAt());
            if (!gameQuestions.isEmpty()) {
                gameState.setCurrentQuestionId(gameQuestions.get(0).getQuestion().getId());
                gameState.setQuestionStartTime(game.getStartedAt());
            }
            gameState.updateActivity();
            
            // Initialize player scores in Redis
            List<GamePlayer> players = gamePlayerRepository.findByGameIdAndLeftAtIsNull(game.getId());
            for (GamePlayer player : players) {
                gameState.addPlayerScore(player.getUser().getId().toString(), 0);
            }
            
            gameStateRepository.saveGameState(game.getId(), gameState);
            log.debug("Updated Redis game state for started game {}", game.getId());
        } catch (Exception e) {
            log.error("Failed to update Redis game state for started game {}: {}", game.getId(), e.getMessage(), e);
            // Continue without Redis - game will work with database only
        }
    }

    /**
     * Finalize game in Redis: mark as completed, update leaderboards, and cleanup
     */
    private void finalizeGameInRedis(Game game) {
        // Update Redis game state to completed before clearing
        try {
            GameStateRepository.GameState gameState = gameStateRepository.getGameState(game.getId());
            if (gameState != null) {
                gameState.setStatus("COMPLETED");
                gameState.updateActivity();
                gameStateRepository.saveGameState(game.getId(), gameState);
                log.debug("Marked game {} as completed in Redis", game.getId());
            }
        } catch (Exception e) {
            log.error("Failed to mark game {} as completed in Redis: {}", game.getId(), e.getMessage(), e);
        }
        
        // Update leaderboards based on game type
        if (isPublicGame(game)) {
            // Public game: Update global leaderboards
            try {
                redisLeaderboardService.addScore(game.getUser().getId().toString(), game.getScore());
                log.debug("Updated global leaderboards for user {} with score {} (public game)", game.getUser().getId(), game.getScore());
            } catch (Exception e) {
                log.error("Failed to update global leaderboards for user {}: {}", game.getUser().getId(), e.getMessage(), e);
            }
        } else {
            // Private game: Update private game leaderboard only
            try {
                redisLeaderboardService.addPrivateGameScore(game.getId(), game.getUser().getId().toString(), game.getScore());
                log.debug("Updated private game leaderboard for user {} with score {} (private game {})", 
                         game.getUser().getId(), game.getScore(), game.getId());
            } catch (Exception e) {
                log.error("Failed to update private game leaderboard for user {}: {}", game.getUser().getId(), e.getMessage(), e);
            }
        }
        
        log.debug("Game completion logged for game {}", game.getId());

        // Clear Redis game data after updating leaderboards
        try {
            gameStateRepository.clearAllGameData(game.getId());
            
            // Also clear private game leaderboard if it's a private game
            if (isPrivateGame(game)) {
                redisLeaderboardService.clearPrivateGameLeaderboard(game.getId());
            }
            
            log.debug("Cleared Redis data for completed game {}", game.getId());
        } catch (Exception e) {
            log.error("Failed to clear Redis data for completed game {}: {}", game.getId(), e.getMessage(), e);
        }
    }

    /**
     * Determines if a game is public (should affect global leaderboards)
     * Public games: No invite code AND multiplayer games for competitive play
     * Private games: Have invite code OR single-player practice games
     */
    private boolean isPublicGame(Game game) {
        // Games with invite codes are always private (friends/family games)
        if (game.getInviteCode() != null && !game.getInviteCode().trim().isEmpty()) {
            return false;
        }
        
        // Single-player games are considered practice/private
        if (game.getMaxPlayers() == 1) {
            return false;
        }
        
        // Multiplayer games without invite codes are public competitive games
        return true;
    }

    /**
     * Determines if a game is private (friends/family casual games)
     */
    private boolean isPrivateGame(Game game) {
        return !isPublicGame(game);
    }

    /**
     * Calculate final score with completion and accuracy bonuses for single-player games
     */
    private void calculateSinglePlayerFinalScore(Game game) {
        try {
            int completionBonus = scoreService.calculateGameCompletionBonus(
                    game.getCorrectAnswers(),
                    game.getGameQuestions().size(),
                    game.getDurationSeconds() != null ? game.getDurationSeconds() : 0
            );

            double accuracyBonus = scoreService.calculateAccuracyBonus(
                    game.getCorrectAnswers(),
                    game.getQuestionsAnswered()
            );

            int finalScore = scoreService.calculateFinalGameScore(
                    game.getScore(),
                    completionBonus,
                    accuracyBonus
            );

            game.setScore(finalScore);
            
            log.debug("Single-player final score calculated: gameId={}, baseScore={}, completionBonus={}, accuracyBonus={}, finalScore={}", 
                     game.getId(), game.getScore() - completionBonus - (int)accuracyBonus, completionBonus, accuracyBonus, finalScore);
        } catch (Exception e) {
            log.error("Failed to calculate single-player final score for game {}: {}", game.getId(), e.getMessage(), e);
            // Don't throw exception to avoid breaking the game end flow
        }
    }

}