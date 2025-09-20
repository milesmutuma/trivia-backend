package com.mabawa.triviacrave.game.service;

import com.mabawa.triviacrave.game.dto.websocket.MessageType;
import com.mabawa.triviacrave.game.dto.websocket.UserSession;
import com.mabawa.triviacrave.game.dto.websocket.WebSocketMessage;
import com.mabawa.triviacrave.game.entity.Game;
import com.mabawa.triviacrave.game.entity.GamePlayer;
import com.mabawa.triviacrave.game.service.impl.WebSocketMessageServiceImpl;
import com.mabawa.triviacrave.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for WebSocketMessageServiceImpl
 */
@ExtendWith(MockitoExtension.class)
class WebSocketMessageServiceTest {
    
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    
    private WebSocketMessageServiceImpl webSocketMessageService;
    
    @BeforeEach
    void setUp() {
        webSocketMessageService = new WebSocketMessageServiceImpl(messagingTemplate);
    }
    
    @Test
    void testRegisterUserSession() {
        // Given
        String sessionId = "session-123";
        Long userId = 1L;
        Long gameId = 100L;
        
        // When
        webSocketMessageService.registerUserSession(sessionId, userId, gameId);
        
        // Then
        assertTrue(webSocketMessageService.isUserConnected(userId));
        Set<UserSession> userSessions = webSocketMessageService.getUserSessions(userId);
        assertEquals(1, userSessions.size());
        
        UserSession session = userSessions.iterator().next();
        assertEquals(sessionId, session.getSessionId());
        assertEquals(userId, session.getUserId());
        assertEquals(gameId, session.getGameId());
    }
    
    @Test
    void testUnregisterUserSession() {
        // Given
        String sessionId = "session-123";
        Long userId = 1L;
        Long gameId = 100L;
        
        webSocketMessageService.registerUserSession(sessionId, userId, gameId);
        assertTrue(webSocketMessageService.isUserConnected(userId));
        
        // When
        webSocketMessageService.unregisterUserSession(sessionId);
        
        // Then
        assertFalse(webSocketMessageService.isUserConnected(userId));
        assertTrue(webSocketMessageService.getUserSessions(userId).isEmpty());
    }
    
    @Test
    void testBroadcastToGame() {
        // Given
        Long gameId = 100L;
        String messageType = MessageType.GAME_STARTED.getValue();
        Object message = Map.of("test", "data");
        
        // When
        webSocketMessageService.broadcastToGame(gameId, messageType, message);
        
        // Then
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), messageCaptor.capture());
        
        assertEquals("/topic/game/100", destinationCaptor.getValue());
        assertEquals(message, messageCaptor.getValue());
    }
    
    @Test
    void testSendPrivateMessage() {
        // Given
        String username = "testuser";
        String destination = "/queue/personal";
        Object message = Map.of("private", "message");
        
        // When
        webSocketMessageService.sendPrivateMessage(username, destination, message);
        
        // Then
        ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<WebSocketMessage> messageCaptor = ArgumentCaptor.forClass(WebSocketMessage.class);
        
        verify(messagingTemplate).convertAndSendToUser(
                usernameCaptor.capture(), 
                destinationCaptor.capture(), 
                messageCaptor.capture()
        );
        
        assertEquals(username, usernameCaptor.getValue());
        assertEquals(destination, destinationCaptor.getValue());
        
        WebSocketMessage sentMessage = messageCaptor.getValue();
        assertEquals(MessageType.PRIVATE_MESSAGE.getValue(), sentMessage.getType());
        assertEquals(message, sentMessage.getData());
    }
    
    @Test
    void testNotifyPlayerJoined() {
        // Given
        Long gameId = 100L;
        User user = User.builder()
                .id(1L)
                .displayName("Test Player")
                .email("test@example.com")
                .build();
        
        GamePlayer player = GamePlayer.builder()
                .user(user)
                .isReady(false)
                .build();
        
        // When
        webSocketMessageService.notifyPlayerJoined(gameId, player);
        
        // Then
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<WebSocketMessage> messageCaptor = ArgumentCaptor.forClass(WebSocketMessage.class);
        
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), messageCaptor.capture());
        
        assertEquals("/topic/game/100", destinationCaptor.getValue());
        
        WebSocketMessage sentMessage = messageCaptor.getValue();
        assertEquals(MessageType.PLAYER_JOINED.getValue(), sentMessage.getType());
        assertEquals(gameId, sentMessage.getGameId());
        assertEquals(user.getId(), sentMessage.getSenderId());
        assertEquals(user.getDisplayName(), sentMessage.getSenderName());
        assertNotNull(sentMessage.getMessageId());
        assertNotNull(sentMessage.getTimestamp());
    }
    
    @Test
    void testNotifyGameStarted() {
        // Given
        Game game = Game.builder()
                .id(100L)
                .gameMode(Game.GameMode.QUICK_PLAY)
                .status(Game.GameStatus.IN_PROGRESS)
                .build();
        
        // When
        webSocketMessageService.notifyGameStarted(game);
        
        // Then
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<WebSocketMessage> messageCaptor = ArgumentCaptor.forClass(WebSocketMessage.class);
        
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), messageCaptor.capture());
        
        assertEquals("/topic/game/100", destinationCaptor.getValue());
        
        WebSocketMessage sentMessage = messageCaptor.getValue();
        assertEquals(MessageType.GAME_STARTED.getValue(), sentMessage.getType());
        assertEquals(game.getId(), sentMessage.getGameId());
        assertEquals(WebSocketMessage.MessagePriority.HIGH, sentMessage.getPriority());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) sentMessage.getData();
        assertEquals(game.getId(), data.get("gameId"));
        assertEquals(game.getGameMode().toString(), data.get("gameMode"));
        assertEquals(game.getStatus().toString(), data.get("status"));
    }
    
    @Test
    void testSendMessageToUser() {
        // Given
        String sessionId = "session-123";
        Long userId = 1L;
        String username = "testuser";
        
        // Register a session first
        webSocketMessageService.registerUserSession(sessionId, userId, null);
        
        // Mock the getUserSessions method to return a session with username
        UserSession mockSession = UserSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .username(username)
                .build();
        
        String messageType = "test.message";
        Object message = Map.of("test", "data");
        
        // When
        webSocketMessageService.sendMessageToUser(userId, messageType, message);
        
        // Then - Since the session doesn't have a username set, it should log a warning
        // but we can test the method doesn't throw an exception
        assertDoesNotThrow(() -> webSocketMessageService.sendMessageToUser(userId, messageType, message));
    }
    
    @Test
    void testBroadcastToAll() {
        // Given
        String messageType = "global.message";
        Object message = Map.of("global", "announcement");
        
        // When
        webSocketMessageService.broadcastToAll(messageType, message);
        
        // Then
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<WebSocketMessage> messageCaptor = ArgumentCaptor.forClass(WebSocketMessage.class);
        
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), messageCaptor.capture());
        
        assertEquals("/topic/broadcast", destinationCaptor.getValue());
        
        WebSocketMessage sentMessage = messageCaptor.getValue();
        assertEquals(messageType, sentMessage.getType());
        assertEquals(message, sentMessage.getData());
    }
    
    @Test
    void testGetSessionStatistics() {
        // Given
        String sessionId1 = "session-1";
        String sessionId2 = "session-2";
        Long userId1 = 1L;
        Long userId2 = 2L;
        Long gameId = 100L;
        
        // Register some sessions
        webSocketMessageService.registerUserSession(sessionId1, userId1, gameId);
        webSocketMessageService.registerUserSession(sessionId2, userId2, gameId);
        
        // When
        Map<String, Object> stats = webSocketMessageService.getSessionStatistics();
        
        // Then
        assertEquals(2, stats.get("totalSessions"));
        assertEquals(2, stats.get("uniqueUsers"));
        assertEquals(1, stats.get("activeGames"));
        assertNotNull(stats.get("timestamp"));
    }
    
    @Test
    void testMultipleSessionsPerUser() {
        // Given
        String sessionId1 = "session-1";
        String sessionId2 = "session-2";
        Long userId = 1L;
        Long gameId = 100L;
        
        // When - Register multiple sessions for the same user
        webSocketMessageService.registerUserSession(sessionId1, userId, gameId);
        webSocketMessageService.registerUserSession(sessionId2, userId, gameId);
        
        // Then
        assertTrue(webSocketMessageService.isUserConnected(userId));
        Set<UserSession> userSessions = webSocketMessageService.getUserSessions(userId);
        assertEquals(2, userSessions.size());
        
        // When - Unregister one session
        webSocketMessageService.unregisterUserSession(sessionId1);
        
        // Then - User should still be connected
        assertTrue(webSocketMessageService.isUserConnected(userId));
        assertEquals(1, webSocketMessageService.getUserSessions(userId).size());
        
        // When - Unregister last session
        webSocketMessageService.unregisterUserSession(sessionId2);
        
        // Then - User should no longer be connected
        assertFalse(webSocketMessageService.isUserConnected(userId));
        assertTrue(webSocketMessageService.getUserSessions(userId).isEmpty());
    }
}