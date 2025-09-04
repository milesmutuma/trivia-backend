package com.mabawa.triviacrave.game.resources;

import com.mabawa.triviacrave.generated.graphql.types.*;
import com.mabawa.triviacrave.game.service.StatsService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@DgsComponent
@RequiredArgsConstructor
public class StatsDataFetcher {
    private final StatsService statsService;

    // User Statistics - Users can see their own stats, admins can see any user's stats
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN') or (hasRole('USER') and #command.userId == authentication.principal.id)")
    public UserStats getUserStats(@InputArgument UserStatsCmd command) {
        return statsService.getUserStats(command);
    }

    @DgsQuery
    @PreAuthorize("hasRole('USER')")
    public UserStats getMyStats(@InputArgument Long categoryId, @InputArgument GameMode gameMode, @InputArgument TimeRange timeRange) {
        return statsService.getMyStats(categoryId, gameMode, timeRange);
    }

    // Leaderboards - Public access
    @DgsQuery
    @PreAuthorize("permitAll()")
    public Leaderboard getLeaderboard(@InputArgument LeaderboardCmd command) {
        return statsService.getLeaderboard(command);
    }

    @DgsQuery
    @PreAuthorize("permitAll()")
    public List<Leaderboard> getLeaderboards(@InputArgument List<LeaderboardType> types, @InputArgument TimeRange timeRange, @InputArgument Integer limit) {
        return statsService.getLeaderboards(types, timeRange, limit);
    }

    // Global Statistics - Public access
    @DgsQuery
    @PreAuthorize("permitAll()")
    public GlobalStats getGlobalStats() {
        return statsService.getGlobalStats();
    }

    // Achievements - Users can see any user's achievements (public), but only their own detailed achievements
    @DgsQuery
    @PreAuthorize("permitAll()")
    public List<Achievement> getUserAchievements(@InputArgument Long userId) {
        return statsService.getUserAchievements(userId);
    }

    @DgsQuery
    @PreAuthorize("hasRole('USER')")
    public List<Achievement> getMyAchievements() {
        return statsService.getMyAchievements();
    }

    // Stats Comparison - Public access for comparing any two users
    @DgsQuery
    @PreAuthorize("permitAll()")
    public StatsComparison compareStats(@InputArgument Long userId1, @InputArgument Long userId2, @InputArgument Long categoryId, @InputArgument GameMode gameMode) {
        return statsService.compareStats(userId1, userId2, categoryId, gameMode);
    }
}