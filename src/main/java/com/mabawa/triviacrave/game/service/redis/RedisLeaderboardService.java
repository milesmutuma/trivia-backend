package com.mabawa.triviacrave.game.service.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLeaderboardService {
    
    private final RedisTemplate<String, String> stringRedisTemplate;
    
    private static final String DAILY_LEADERBOARD_PREFIX = "leaderboard:daily:";
    private static final String WEEKLY_LEADERBOARD_PREFIX = "leaderboard:weekly:";
    private static final String MONTHLY_LEADERBOARD_PREFIX = "leaderboard:monthly:";
    private static final String ALL_TIME_LEADERBOARD = "leaderboard:alltime";
    private static final String PRIVATE_GAME_LEADERBOARD_PREFIX = "game:private:";
    
    /**
     * Add or update a player's score in all relevant leaderboards
     */
    public void addScore(String userId, double score) {
        LocalDate today = LocalDate.now();
        
        try {
            // Add to daily leaderboard
            String dailyKey = getDailyLeaderboardKey(today);
            stringRedisTemplate.opsForZSet().incrementScore(dailyKey, userId, score);
            
            // Add to weekly leaderboard
            String weeklyKey = getWeeklyLeaderboardKey(today);
            stringRedisTemplate.opsForZSet().incrementScore(weeklyKey, userId, score);
            
            // Add to monthly leaderboard
            String monthlyKey = getMonthlyLeaderboardKey(today);
            stringRedisTemplate.opsForZSet().incrementScore(monthlyKey, userId, score);
            
            // Add to all-time leaderboard
            stringRedisTemplate.opsForZSet().incrementScore(ALL_TIME_LEADERBOARD, userId, score);
            
            log.debug("Added score {} for user {} to leaderboards", score, userId);
            
        } catch (Exception e) {
            log.error("Error adding score to leaderboards for user {}: {}", userId, e.getMessage(), e);
        }
    }
    
    /**
     * Get top players from daily leaderboard
     */
    public List<LeaderboardEntry> getTopPlayersDaily(int limit) {
        return getTopPlayersFromKey(getDailyLeaderboardKey(LocalDate.now()), limit);
    }
    
    /**
     * Get top players from weekly leaderboard
     */
    public List<LeaderboardEntry> getTopPlayersWeekly(int limit) {
        return getTopPlayersFromKey(getWeeklyLeaderboardKey(LocalDate.now()), limit);
    }
    
    /**
     * Get top players from monthly leaderboard
     */
    public List<LeaderboardEntry> getTopPlayersMonthly(int limit) {
        return getTopPlayersFromKey(getMonthlyLeaderboardKey(LocalDate.now()), limit);
    }
    
    /**
     * Get top players from all-time leaderboard
     */
    public List<LeaderboardEntry> getTopPlayersAllTime(int limit) {
        return getTopPlayersFromKey(ALL_TIME_LEADERBOARD, limit);
    }
    
    /**
     * Get user's rank in daily leaderboard (0-based, add 1 for display)
     */
    public Long getUserRankDaily(String userId) {
        return stringRedisTemplate.opsForZSet().reverseRank(getDailyLeaderboardKey(LocalDate.now()), userId);
    }
    
    /**
     * Get user's rank in weekly leaderboard (0-based, add 1 for display)
     */
    public Long getUserRankWeekly(String userId) {
        return stringRedisTemplate.opsForZSet().reverseRank(getWeeklyLeaderboardKey(LocalDate.now()), userId);
    }
    
    /**
     * Get user's rank in monthly leaderboard (0-based, add 1 for display)
     */
    public Long getUserRankMonthly(String userId) {
        return stringRedisTemplate.opsForZSet().reverseRank(getMonthlyLeaderboardKey(LocalDate.now()), userId);
    }
    
    /**
     * Get user's rank in all-time leaderboard (0-based, add 1 for display)
     */
    public Long getUserRankAllTime(String userId) {
        return stringRedisTemplate.opsForZSet().reverseRank(ALL_TIME_LEADERBOARD, userId);
    }
    
    /**
     * Get user's score from daily leaderboard
     */
    public Double getUserScoreDaily(String userId) {
        return stringRedisTemplate.opsForZSet().score(getDailyLeaderboardKey(LocalDate.now()), userId);
    }
    
    /**
     * Get players around a specific user (e.g., 5 above and 5 below)
     */
    public List<LeaderboardEntry> getPlayersAroundUser(String userId, String leaderboardType, int range) {
        String key = getLeaderboardKey(leaderboardType);
        Long rank = stringRedisTemplate.opsForZSet().reverseRank(key, userId);
        
        if (rank == null) {
            return List.of(); // User not found in leaderboard
        }
        
        long start = Math.max(0, rank - range);
        long end = rank + range;
        
        return getTopPlayersFromKey(key, start, end);
    }
    
    /**
     * Get total number of players in a leaderboard
     */
    public Long getLeaderboardSize(String leaderboardType) {
        String key = getLeaderboardKey(leaderboardType);
        return stringRedisTemplate.opsForZSet().zCard(key);
    }
    
    // Private helper methods
    
    private List<LeaderboardEntry> getTopPlayersFromKey(String key, int limit) {
        return getTopPlayersFromKey(key, 0, limit - 1);
    }
    
    private List<LeaderboardEntry> getTopPlayersFromKey(String key, long start, long end) {
        Set<ZSetOperations.TypedTuple<String>> topScores = 
            stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        
        return topScores.stream()
            .map(tuple -> new LeaderboardEntry(
                tuple.getValue(),
                tuple.getScore() != null ? tuple.getScore() : 0.0,
                start + topScores.stream().collect(Collectors.toList()).indexOf(tuple) + 1 // Calculate rank
            ))
            .collect(Collectors.toList());
    }
    
    private String getDailyLeaderboardKey(LocalDate date) {
        return DAILY_LEADERBOARD_PREFIX + date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
    
    private String getWeeklyLeaderboardKey(LocalDate date) {
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        int year = date.getYear();
        int week = date.get(weekFields.weekOfWeekBasedYear());
        return WEEKLY_LEADERBOARD_PREFIX + year + "-W" + String.format("%02d", week);
    }
    
    private String getMonthlyLeaderboardKey(LocalDate date) {
        return MONTHLY_LEADERBOARD_PREFIX + date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }
    
    private String getLeaderboardKey(String leaderboardType) {
        return switch (leaderboardType.toLowerCase()) {
            case "daily" -> getDailyLeaderboardKey(LocalDate.now());
            case "weekly" -> getWeeklyLeaderboardKey(LocalDate.now());
            case "monthly" -> getMonthlyLeaderboardKey(LocalDate.now());
            case "alltime" -> ALL_TIME_LEADERBOARD;
            default -> throw new IllegalArgumentException("Invalid leaderboard type: " + leaderboardType);
        };
    }
    
    /**
     * Add score to private game leaderboard (does not affect global rankings)
     */
    public void addPrivateGameScore(Long gameId, String userId, double score) {
        try {
            String privateKey = getPrivateGameLeaderboardKey(gameId);
            stringRedisTemplate.opsForZSet().incrementScore(privateKey, userId, score);
            
            // Set expiry for private game leaderboard (24 hours after last update)
            stringRedisTemplate.expire(privateKey, Duration.ofHours(24));
            
            log.debug("Added score {} for user {} to private game {} leaderboard", score, userId, gameId);
            
        } catch (Exception e) {
            log.error("Error adding score to private game leaderboard: gameId={}, userId={}, error={}", 
                     gameId, userId, e.getMessage(), e);
        }
    }
    
    /**
     * Get rankings for a private game
     */
    public List<LeaderboardEntry> getPrivateGameRankings(Long gameId, int limit) {
        return getTopPlayersFromKey(getPrivateGameLeaderboardKey(gameId), limit);
    }
    
    /**
     * Get user's rank in a private game (0-based)
     */
    public Long getUserRankInPrivateGame(Long gameId, String userId) {
        return stringRedisTemplate.opsForZSet().reverseRank(getPrivateGameLeaderboardKey(gameId), userId);
    }
    
    /**
     * Get user's score in a private game
     */
    public Double getUserScoreInPrivateGame(Long gameId, String userId) {
        return stringRedisTemplate.opsForZSet().score(getPrivateGameLeaderboardKey(gameId), userId);
    }
    
    /**
     * Clear private game leaderboard (called when game ends)
     */
    public void clearPrivateGameLeaderboard(Long gameId) {
        try {
            String privateKey = getPrivateGameLeaderboardKey(gameId);
            stringRedisTemplate.delete(privateKey);
            log.debug("Cleared private game leaderboard for game {}", gameId);
        } catch (Exception e) {
            log.error("Error clearing private game leaderboard for game {}: {}", gameId, e.getMessage(), e);
        }
    }
    
    private String getPrivateGameLeaderboardKey(Long gameId) {
        return PRIVATE_GAME_LEADERBOARD_PREFIX + gameId;
    }
    
    /**
     * Leaderboard entry representing a player's position and score
     */
    public record LeaderboardEntry(String userId, double score, long rank) {
    }
}