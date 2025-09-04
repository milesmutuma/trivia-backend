package com.mabawa.triviacrave.game.service;

import com.mabawa.triviacrave.generated.graphql.types.*;

public interface GameService {
    // Game creation and management - return ApiResponse
    ApiResponse createGame(CreateGameCmd cmd);
    ApiResponse startGame(StartGameCmd cmd);
    ApiResponse joinGame(JoinGameCmd cmd);
    ApiResponse leaveGame(Long gameId, Long userId);
    ApiResponse setPlayerReady(Long gameId, Long userId, Boolean ready);
    
    // Game play methods - return ApiResponse
    ApiResponse submitAnswer(SubmitAnswerCmd cmd);
    ApiResponse endGame(EndGameCmd cmd);
    ApiResponse pauseGame(Long gameId, Long userId);
    ApiResponse resumeGame(Long gameId, Long userId);
    ApiResponse abandonGame(Long gameId, Long userId);
    
    // Query methods - return direct types for GraphQL queries
    Game getActiveGame(); // Uses authenticated user from security context
    ActiveGameStatus getActiveGameStatus(); // Uses authenticated user from security context
    Game getGame(Long gameId); // Uses authenticated user from security context for authorization
    GameLobby getGameLobby(Long gameId); // Get game lobby with players
    java.util.List<Game> getOpenGames(GameMode mode, Integer limit); // Public games waiting for players
    java.util.List<Game> getGameHistory(Long userId, Integer limit, Integer offset);
    java.util.List<Game> getGameHistory(Integer limit, Integer offset); // Uses authenticated user from security context
    java.util.List<Game> getGamesByMode(GameMode mode, Integer limit, Integer offset);
}