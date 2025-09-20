package com.mabawa.triviacrave.game.dto.websocket;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Base WebSocket message structure for real-time communication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {
    
    /**
     * Type of the message (PLAYER_JOINED, GAME_STARTED, QUESTION, SCORE_UPDATE, etc.)
     */
    private String type;
    
    /**
     * Game ID associated with this message (null for global messages)
     */
    private Long gameId;
    
    /**
     * User ID of the sender (null for system messages)
     */
    private Long senderId;
    
    /**
     * Display name of the sender (null for system messages)
     */
    private String senderName;
    
    /**
     * Message content/payload
     */
    private Object data;
    
    /**
     * Timestamp when the message was created
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * Optional message ID for tracking/acknowledgment
     */
    private String messageId;
    
    /**
     * Message priority (HIGH, NORMAL, LOW)
     */
    @Builder.Default
    private MessagePriority priority = MessagePriority.NORMAL;
    
    public enum MessagePriority {
        HIGH, NORMAL, LOW
    }
}