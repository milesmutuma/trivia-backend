package com.mabawa.triviacrave.game.dto.websocket;

/**
 * Enum defining all WebSocket message types for the trivia game
 */
public enum MessageType {
    // Player events
    PLAYER_JOINED("player.joined"),
    PLAYER_LEFT("player.left"),
    PLAYER_READINESS("player.readiness"),
    CONNECTION_STATUS("connection.status"),
    
    // Game state events
    GAME_STARTED("game.started"),
    GAME_ENDED("game.ended"),
    GAME_STATE_CHANGE("game.state.change"),
    GAME_PAUSED("game.paused"),
    GAME_RESUMED("game.resumed"),
    
    // Question events
    QUESTION_SENT("question.sent"),
    QUESTION_TIMEOUT("question.timeout"),
    ANSWER_RECEIVED("answer.received"),
    
    // Score events
    SCORE_UPDATE("score.update"),
    LEADERBOARD_UPDATE("leaderboard.update"),
    FINAL_RESULTS("final.results"),
    
    // System events
    SYSTEM_MESSAGE("system.message"),
    ERROR_MESSAGE("error.message"),
    PRIVATE_MESSAGE("private.message"),
    
    // Generic events
    BROADCAST("broadcast"),
    NOTIFICATION("notification");
    
    private final String value;
    
    MessageType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    @Override
    public String toString() {
        return value;
    }
}