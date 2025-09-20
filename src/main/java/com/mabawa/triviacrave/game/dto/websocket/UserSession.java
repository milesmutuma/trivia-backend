package com.mabawa.triviacrave.game.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a user's WebSocket session information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {
    
    /**
     * Unique session identifier (WebSocket session ID)
     */
    private String sessionId;
    
    /**
     * User ID associated with this session
     */
    private Long userId;
    
    /**
     * User's email/username
     */
    private String username;
    
    /**
     * User's display name
     */
    private String displayName;
    
    /**
     * Current game ID (null if not in a game)
     */
    private Long gameId;
    
    /**
     * When this session was established
     */
    @Builder.Default
    private LocalDateTime connectedAt = LocalDateTime.now();
    
    /**
     * Last activity timestamp
     */
    @Builder.Default
    private LocalDateTime lastActivity = LocalDateTime.now();
    
    /**
     * Whether this session is currently active
     */
    @Builder.Default
    private boolean active = true;
    
    /**
     * Update the last activity timestamp
     */
    public void updateActivity() {
        this.lastActivity = LocalDateTime.now();
    }
    
    /**
     * Check if session has been inactive for too long
     */
    public boolean isStale(int timeoutMinutes) {
        return lastActivity.isBefore(LocalDateTime.now().minusMinutes(timeoutMinutes));
    }
}