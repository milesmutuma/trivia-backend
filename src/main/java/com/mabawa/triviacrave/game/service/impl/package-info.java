/**
 * WebSocket Service Implementation Package
 * 
 * This package contains the complete implementation of WebSocket messaging functionality
 * for the trivia game application. The implementation provides real-time communication
 * capabilities with session management, message broadcasting, and integration with 
 * Spring WebSocket/STOMP protocol.
 * 
 * Key Components:
 * 
 * 1. WebSocketMessageServiceImpl
 *    - Complete implementation of WebSocketMessageService interface
 *    - Real-time messaging using SimpMessagingTemplate
 *    - Thread-safe session management with ConcurrentHashMap
 *    - Support for game-specific and user-specific messaging
 *    - Automatic session cleanup and monitoring
 *    - Comprehensive error handling and logging
 * 
 * 2. GameWebSocketIntegrationService
 *    - Integration layer between game logic and WebSocket messaging
 *    - Handles complex game events with multiple notifications
 *    - Provides countdown timers, player updates, and state changes
 *    - Used by GameServiceImpl and LiveGameOrchestrator
 * 
 * Key Features Implemented:
 * 
 * Session Management:
 * - User session registration and tracking
 * - Multiple session support per user (multi-device)
 * - Automatic session cleanup with configurable timeout
 * - Game-specific session grouping
 * 
 * Messaging Capabilities:
 * - Broadcast to all players in a game (/topic/game/{gameId})
 * - Private messages to specific users (/queue/{username})
 * - Global broadcasts to all connected users (/topic/broadcast)
 * - Connection status notifications (/topic/connection)
 * 
 * Message Types Supported:
 * - Player join/leave notifications
 * - Game state changes (start, pause, end)
 * - Question distribution with timing
 * - Score updates and leaderboards
 * - Real-time chat messages
 * - System notifications and errors
 * 
 * WebSocket Destinations:
 * - /topic/game/{gameId} - Game-specific broadcasts
 * - /queue/{username} - User-specific private messages
 * - /topic/broadcast - Global announcements
 * - /topic/connection - Connection status updates
 * 
 * Integration Points:
 * - Spring WebSocket with STOMP protocol
 * - JWT authentication integration
 * - Redis for session persistence (if needed)
 * - Game service layer integration
 * - LiveGameOrchestrator integration
 * 
 * Usage Example:
 * 
 * // Register user session when they connect
 * webSocketMessageService.registerUserSession(sessionId, userId, gameId);
 * 
 * // Broadcast game event to all players
 * webSocketMessageService.broadcastToGame(gameId, "GAME_STARTED", gameData);
 * 
 * // Send private message to specific user
 * webSocketMessageService.sendPrivateMessage(username, "/queue/personal", message);
 * 
 * // Clean up when user disconnects
 * webSocketMessageService.unregisterUserSession(sessionId);
 * 
 * Performance Considerations:
 * - Thread-safe collections for concurrent access
 * - Scheduled cleanup of stale sessions (every 5 minutes)
 * - Efficient message routing with minimal overhead
 * - Graceful error handling to prevent service disruption
 * 
 * Security Features:
 * - Session validation and cleanup
 * - User authentication integration
 * - Message sanitization and validation
 * - Connection tracking and monitoring
 * 
 * Monitoring and Debugging:
 * - Comprehensive logging at appropriate levels
 * - Session statistics for monitoring
 * - Error tracking and reporting
 * - Activity timestamp tracking
 * 
 * @author WebSocket Implementation Team
 * @version 1.0
 * @since 2025-09-13
 */
package com.mabawa.triviacrave.game.service.impl;