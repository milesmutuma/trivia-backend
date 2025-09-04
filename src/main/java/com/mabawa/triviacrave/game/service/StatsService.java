package com.mabawa.triviacrave.game.service;

import com.mabawa.triviacrave.generated.graphql.types.*;

public interface StatsService {
    // Background processing methods - return void or ApiResponse for consistency
    void updateUserStatsAfterGame(Long userId, Long gameId);
    void calculateAndUpdateRankings();
    void checkAndAwardAchievements(Long userId, Long gameId);
    
    // Query methods - return direct types for GraphQL queries
    UserStats getUserStats(UserStatsCmd cmd);
    UserStats getMyStats(Long categoryId, GameMode gameMode, TimeRange timeRange); // Uses authenticated user from security context
    Leaderboard getLeaderboard(LeaderboardCmd cmd);
    java.util.List<Leaderboard> getLeaderboards(java.util.List<LeaderboardType> types, TimeRange timeRange, Integer limit);
    GlobalStats getGlobalStats();
    java.util.List<Achievement> getUserAchievements(Long userId);
    java.util.List<Achievement> getMyAchievements(); // Uses authenticated user from security context
    StatsComparison compareStats(Long userId1, Long userId2, Long categoryId, GameMode gameMode);
}