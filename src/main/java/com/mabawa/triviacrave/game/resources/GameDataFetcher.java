package com.mabawa.triviacrave.game.resources;

import com.mabawa.triviacrave.generated.graphql.types.*;
import com.mabawa.triviacrave.game.service.GameService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@DgsComponent
@RequiredArgsConstructor
public class GameDataFetcher {
    private final GameService gameService;

    @DgsMutation
    @PreAuthorize("hasRole('USER')")
    public ApiResponse createGame(@InputArgument CreateGameCmd command) {
        return gameService.createGame(command);
    }

    @DgsMutation
    @PreAuthorize("hasRole('USER')")
    public ApiResponse startGame(@InputArgument StartGameCmd command) {
        return gameService.startGame(command);
    }

    @DgsMutation
    @PreAuthorize("hasRole('USER')")
    public ApiResponse joinGame(@InputArgument JoinGameCmd command) {
        return gameService.joinGame(command);
    }

    @DgsMutation
    @PreAuthorize("hasRole('USER')")
    public ApiResponse leaveGame(@InputArgument Long gameId, @InputArgument Long userId) {
        return gameService.leaveGame(gameId, userId);
    }

    @DgsMutation
    @PreAuthorize("hasRole('USER')")
    public ApiResponse setPlayerReady(@InputArgument Long gameId, @InputArgument Long userId, @InputArgument Boolean ready) {
        return gameService.setPlayerReady(gameId, userId, ready);
    }

    @DgsMutation
    @PreAuthorize("hasRole('USER')")
    public ApiResponse submitAnswer(@InputArgument SubmitAnswerCmd command) {
        return gameService.submitAnswer(command);
    }

    @DgsMutation
    @PreAuthorize("hasRole('USER')")
    public ApiResponse endGame(@InputArgument EndGameCmd command) {
        return gameService.endGame(command);
    }

    @DgsQuery
    @PreAuthorize("hasRole('USER')")
    public Game getActiveGame() {
        return gameService.getActiveGame();
    }

    @DgsQuery
    @PreAuthorize("hasRole('USER')")
    public ActiveGameStatus getActiveGameStatus() {
        return gameService.getActiveGameStatus();
    }

    @DgsQuery
    @PreAuthorize("hasRole('USER')")
    public Game getGame(@InputArgument Long gameId) {
        return gameService.getGame(gameId);
    }

    @DgsQuery
    @PreAuthorize("hasRole('USER')")
    public GameLobby getGameLobby(@InputArgument Long gameId) {
        return gameService.getGameLobby(gameId);
    }

    @DgsQuery
    @PreAuthorize("permitAll()")
    public List<Game> getOpenGames(@InputArgument GameMode mode, @InputArgument Integer limit) {
        return gameService.getOpenGames(mode, limit);
    }

    @DgsQuery
    @PreAuthorize("hasRole('USER')")
    public List<Game> getGameHistory(@InputArgument Long userId, @InputArgument Integer limit, @InputArgument Integer offset) {
        return gameService.getGameHistory(userId, limit, offset);
    }

    @DgsQuery
    @PreAuthorize("hasRole('USER')")
    public List<Game> getMyGameHistory(@InputArgument Integer limit, @InputArgument Integer offset) {
        return gameService.getGameHistory(limit, offset);
    }

    @DgsQuery
    @PreAuthorize("hasRole('USER')")
    public List<Game> getGamesByMode(@InputArgument GameMode mode, @InputArgument Integer limit, @InputArgument Integer offset) {
        return gameService.getGamesByMode(mode, limit, offset);
    }
}