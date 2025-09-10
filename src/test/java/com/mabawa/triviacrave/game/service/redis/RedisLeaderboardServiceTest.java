package com.mabawa.triviacrave.game.service.redis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedisLeaderboardServiceTest {

    @Autowired
    private RedisLeaderboardService redisLeaderboardService;

    @Autowired
    private RedisTemplate<String, String> stringRedisTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Use existing Redis instance
        registry.add("spring.redis.host", () -> "localhost");
        registry.add("spring.redis.port", () -> "6380");
    }

    @Test
    void testAddScoreAndGetTopPlayers() {
        // Clean up any existing test data
        String testKey = "leaderboard:daily:" + java.time.LocalDate.now();
        stringRedisTemplate.delete(testKey);

        // Add some test scores
        redisLeaderboardService.addScore("user1", 1000);
        redisLeaderboardService.addScore("user2", 1500);
        redisLeaderboardService.addScore("user3", 800);

        // Get top players
        List<RedisLeaderboardService.LeaderboardEntry> topPlayers = 
            redisLeaderboardService.getTopPlayersDaily(3);

        // Verify results
        assertNotNull(topPlayers);
        assertEquals(3, topPlayers.size());
        
        // Should be sorted by score descending
        assertEquals("user2", topPlayers.get(0).userId());
        assertEquals(1500.0, topPlayers.get(0).score());
        assertEquals(1L, topPlayers.get(0).rank());
        
        assertEquals("user1", topPlayers.get(1).userId());
        assertEquals(1000.0, topPlayers.get(1).score());
        assertEquals(2L, topPlayers.get(1).rank());
        
        assertEquals("user3", topPlayers.get(2).userId());
        assertEquals(800.0, topPlayers.get(2).score());
        assertEquals(3L, topPlayers.get(2).rank());
    }

    @Test
    void testGetUserRank() {
        // Clean up any existing test data
        String testKey = "leaderboard:daily:" + java.time.LocalDate.now();
        stringRedisTemplate.delete(testKey);

        // Add some test scores
        redisLeaderboardService.addScore("user1", 1000);
        redisLeaderboardService.addScore("user2", 1500);
        redisLeaderboardService.addScore("user3", 800);

        // Test user ranks (0-based)
        Long user1Rank = redisLeaderboardService.getUserRankDaily("user1");
        Long user2Rank = redisLeaderboardService.getUserRankDaily("user2");
        Long user3Rank = redisLeaderboardService.getUserRankDaily("user3");

        assertEquals(1L, user1Rank); // 2nd place (0-based = 1)
        assertEquals(0L, user2Rank); // 1st place (0-based = 0)
        assertEquals(2L, user3Rank); // 3rd place (0-based = 2)
    }

    @Test
    void testIncrementalScoreUpdates() {
        // Clean up any existing test data
        String testKey = "leaderboard:daily:" + java.time.LocalDate.now();
        stringRedisTemplate.delete(testKey);

        // Add initial score
        redisLeaderboardService.addScore("user1", 500);
        Double initialScore = redisLeaderboardService.getUserScoreDaily("user1");
        assertEquals(500.0, initialScore);

        // Add more points (should increment, not replace)
        redisLeaderboardService.addScore("user1", 300);
        Double updatedScore = redisLeaderboardService.getUserScoreDaily("user1");
        assertEquals(800.0, updatedScore);
    }

    @Test
    void testLeaderboardSize() {
        // Clean up any existing test data
        String testKey = "leaderboard:daily:" + java.time.LocalDate.now();
        stringRedisTemplate.delete(testKey);

        // Add some users
        redisLeaderboardService.addScore("user1", 1000);
        redisLeaderboardService.addScore("user2", 1500);
        redisLeaderboardService.addScore("user3", 800);

        // Check leaderboard size
        Long size = redisLeaderboardService.getLeaderboardSize("daily");
        assertEquals(3L, size);
    }

    @Test
    void testGetPlayersAroundUser() {
        // Clean up any existing test data
        String testKey = "leaderboard:daily:" + java.time.LocalDate.now();
        stringRedisTemplate.delete(testKey);

        // Add multiple users with different scores
        for (int i = 1; i <= 10; i++) {
            redisLeaderboardService.addScore("user" + i, i * 100);
        }

        // Get players around user5 (should be rank 5, with score 500)
        List<RedisLeaderboardService.LeaderboardEntry> playersAround = 
            redisLeaderboardService.getPlayersAroundUser("user5", "daily", 2);

        // Should get users with ranks 3-7 (user8, user7, user6, user5, user4)
        assertNotNull(playersAround);
        assertEquals(5, playersAround.size());
        
        // Check that user5 is in the middle
        boolean foundUser5 = playersAround.stream()
            .anyMatch(entry -> "user5".equals(entry.userId()) && entry.score() == 500.0);
        assertTrue(foundUser5);
    }
}