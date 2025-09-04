package com.mabawa.triviacrave.game.service.impl;

import com.mabawa.triviacrave.common.utils.IDGenerator;
import com.mabawa.triviacrave.game.entity.Category;
import com.mabawa.triviacrave.game.entity.Game;
import com.mabawa.triviacrave.game.entity.Question;
import com.mabawa.triviacrave.game.repository.*;
import com.mabawa.triviacrave.game.service.StatsService;
import com.mabawa.triviacrave.generated.graphql.types.Achievement;
import com.mabawa.triviacrave.generated.graphql.types.AchievementType;
import com.mabawa.triviacrave.generated.graphql.types.ApiResponse;
import com.mabawa.triviacrave.generated.graphql.types.ComparisonResult;
import com.mabawa.triviacrave.generated.graphql.types.Difficulty;
import com.mabawa.triviacrave.generated.graphql.types.GameMode;
import com.mabawa.triviacrave.generated.graphql.types.GlobalStats;
import com.mabawa.triviacrave.generated.graphql.types.Leaderboard;
import com.mabawa.triviacrave.generated.graphql.types.LeaderboardCmd;
import com.mabawa.triviacrave.generated.graphql.types.LeaderboardEntry;
import com.mabawa.triviacrave.generated.graphql.types.LeaderboardType;
import com.mabawa.triviacrave.generated.graphql.types.LeaderboardList;
import com.mabawa.triviacrave.generated.graphql.types.AchievementList;
import com.mabawa.triviacrave.generated.graphql.types.MetricComparison;
import com.mabawa.triviacrave.generated.graphql.types.StatsComparison;
import com.mabawa.triviacrave.generated.graphql.types.TimeRange;
import com.mabawa.triviacrave.generated.graphql.types.UserStatsCmd;
import com.mabawa.triviacrave.user.entity.User;
import com.mabawa.triviacrave.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {
    private final UserStatsRepository userStatsRepository;
    private final GameRepository gameRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;

    public ApiResponse getUserStatsApiResponse(UserStatsCmd cmd) {
        try {
            validateUserStatsCmd(cmd);

            User user = userRepository.findOne(cmd.getUserId());
            if (user == null) {
                throw new IllegalArgumentException("User not found");
            }

            com.mabawa.triviacrave.game.entity.UserStats stats = buildUserStats(user, cmd.getCategoryId(), cmd.getGameMode(), cmd.getTimeRange());

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("User statistics retrieved successfully")
                    .data(mapToGraphQLUserStats(stats, user))
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for get user stats: {}", e.getMessage());
            return ApiResponse.newBuilder()
                    .status(400)
                    .message(e.getMessage())
                    .data(null)
                    .build();
        } catch (Exception e) {
            log.error("Error retrieving user stats: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to retrieve user statistics")
                    .data(null)
                    .build();
        }
    }

    public ApiResponse getMyStatsApiResponse(Long userId, Long categoryId, GameMode gameMode, TimeRange timeRange) {
        try {
            if (userId == null) {
                throw new IllegalArgumentException("User ID is required");
            }

            UserStatsCmd cmd = UserStatsCmd.newBuilder()
                    .userId(userId)
                    .categoryId(categoryId)
                    .gameMode(gameMode)
                    .timeRange(timeRange)
                    .build();

            return getUserStatsApiResponse(cmd);

        } catch (Exception e) {
            log.error("Error retrieving my stats: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to retrieve your statistics")
                    .data(null)
                    .build();
        }
    }

    public ApiResponse getLeaderboardApiResponse(LeaderboardCmd cmd) {
        try {
            validateLeaderboardCmd(cmd);

            Leaderboard leaderboard = buildLeaderboard(cmd);

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Leaderboard retrieved successfully")
                    .data(leaderboard)
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for get leaderboard: {}", e.getMessage());
            return ApiResponse.newBuilder()
                    .status(400)
                    .message(e.getMessage())
                    .data(null)
                    .build();
        } catch (Exception e) {
            log.error("Error retrieving leaderboard: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to retrieve leaderboard")
                    .data(null)
                    .build();
        }
    }

    public ApiResponse getLeaderboardsApiResponse(List<LeaderboardType> types, TimeRange timeRange, Integer limit) {
        try {
            if (types == null || types.isEmpty()) {
                throw new IllegalArgumentException("Leaderboard types are required");
            }

            List<Leaderboard> leaderboards = new ArrayList<>();

            for (LeaderboardType type : types) {
                LeaderboardCmd cmd = LeaderboardCmd.newBuilder()
                        .type(type)
                        .timeRange(timeRange != null ? timeRange : TimeRange.ALL_TIME)
                        .limit(limit)
                        .build();
                
                Leaderboard leaderboard = buildLeaderboard(cmd);
                leaderboards.add(leaderboard);
            }

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Leaderboards retrieved successfully")
                    .data(LeaderboardList.newBuilder().leaderboards(leaderboards).build())
                    .build();

        } catch (Exception e) {
            log.error("Error retrieving leaderboards: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to retrieve leaderboards")
                    .data(LeaderboardList.newBuilder().leaderboards(List.of()).build())
                    .build();
        }
    }

    public ApiResponse getGlobalStatsApiResponse() {
        try {
            GlobalStats stats = buildGlobalStats();

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Global statistics retrieved successfully")
                    .data(stats)
                    .build();

        } catch (Exception e) {
            log.error("Error retrieving global stats: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to retrieve global statistics")
                    .data(null)
                    .build();
        }
    }

    public ApiResponse getUserAchievementsApiResponse(Long userId) {
        try {
            if (userId == null) {
                throw new IllegalArgumentException("User ID is required");
            }

            User user = userRepository.findOne(userId);
            if (user == null) {
                throw new IllegalArgumentException("User not found");
            }

            List<Achievement> achievements = buildUserAchievements(user);

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("User achievements retrieved successfully")
                    .data(AchievementList.newBuilder().achievements(achievements).build())
                    .build();

        } catch (Exception e) {
            log.error("Error retrieving user achievements: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to retrieve user achievements")
                    .data(AchievementList.newBuilder().achievements(List.of()).build())
                    .build();
        }
    }

    public ApiResponse getMyAchievementsApiResponse(Long userId) {
        List<Achievement> achievements = getUserAchievements(userId);
        return ApiResponse.newBuilder()
                .status(200)
                .message("User achievements retrieved successfully")
                .data(com.mabawa.triviacrave.generated.graphql.types.AchievementList.newBuilder()
                        .achievements(achievements)
                        .build())
                .build();
    }

    public ApiResponse compareStatsApiResponse(Long userId1, Long userId2, Long categoryId, GameMode gameMode) {
        try {
            if (userId1 == null || userId2 == null) {
                throw new IllegalArgumentException("Both user IDs are required");
            }

            User user1 = userRepository.findOne(userId1);
            User user2 = userRepository.findOne(userId2);

            if (user1 == null || user2 == null) {
                throw new IllegalArgumentException("One or both users not found");
            }

            StatsComparison comparison = buildStatsComparison(user1, user2, categoryId, gameMode);

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Statistics comparison retrieved successfully")
                    .data(comparison)
                    .build();

        } catch (Exception e) {
            log.error("Error comparing stats: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to compare statistics")
                    .data(null)
                    .build();
        }
    }

    @Override
    @Transactional
    public void updateUserStatsAfterGame(Long userId, Long gameId) {
        try {
            User user = userRepository.findOne(userId);
            Game game = gameRepository.findOne(gameId);

            if (user == null || game == null || !game.isGameFinished()) {
                return;
            }

            // Update or create user stats for the game's category
            com.mabawa.triviacrave.game.entity.UserStats stats = userStatsRepository.findByUserIdAndCategoryId(userId, game.getCategory().getId())
                    .orElse(com.mabawa.triviacrave.game.entity.UserStats.builder()
                            .id(IDGenerator.generateId())
                            .user(user)
                            .category(game.getCategory())
                            .build());

            stats.updateStatsAfterGame(game);
            userStatsRepository.save(stats);

            log.info("Updated user stats for user {} after game {}", userId, gameId);

        } catch (Exception e) {
            log.error("Error updating user stats after game: {}", e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void calculateAndUpdateRankings() {
        try {
            List<com.mabawa.triviacrave.game.entity.UserStats> allStats = userStatsRepository.findAll();
            
            // Calculate rankings by total score
            allStats.sort((a, b) -> Long.compare(b.getTotalScore(), a.getTotalScore()));
            
            for (int i = 0; i < allStats.size(); i++) {
                com.mabawa.triviacrave.game.entity.UserStats stats = allStats.get(i);
                // This would require adding a rank field to UserStats entity
                // For now, we'll calculate rank dynamically when needed
                userStatsRepository.save(stats);
            }

            log.info("Updated rankings for {} users", allStats.size());

        } catch (Exception e) {
            log.error("Error calculating and updating rankings: {}", e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void checkAndAwardAchievements(Long userId, Long gameId) {
        try {
            User user = userRepository.findOne(userId);
            Game game = gameRepository.findOne(gameId);

            if (user == null || game == null) {
                return;
            }

            List<Achievement> newAchievements = calculateEarnedAchievements(user, game);
            
            // In a real implementation, you would save these achievements to the database
            // For now, we'll just log them
            for (Achievement achievement : newAchievements) {
                log.info("User {} earned achievement: {}", userId, achievement.getName());
            }

        } catch (Exception e) {
            log.error("Error checking and awarding achievements: {}", e.getMessage(), e);
        }
    }

    // Helper methods

    private void validateUserStatsCmd(UserStatsCmd cmd) {
        if (cmd.getUserId() <= 0) {
            throw new IllegalArgumentException("User ID is required");
        }
    }

    private void validateLeaderboardCmd(LeaderboardCmd cmd) {
        if (cmd.getType() == null) {
            throw new IllegalArgumentException("Leaderboard type is required");
        }
    }

    private com.mabawa.triviacrave.game.entity.UserStats buildUserStats(User user, Long categoryId, GameMode gameMode, TimeRange timeRange) {
        // Build comprehensive user stats based on filters
        List<Game> userGames = gameRepository.findGamesByUserId(user.getId());

        // Apply time range filter
        if (timeRange != null && timeRange != TimeRange.ALL_TIME) {
            LocalDateTime cutoff = getTimeRangeCutoff(timeRange);
            userGames = userGames.stream()
                    .filter(g -> g.getStartedAt().isAfter(cutoff))
                    .toList();
        }

        // Apply category filter
        if (categoryId != null) {
            userGames = userGames.stream()
                    .filter(g -> g.getCategory() != null && g.getCategory().getId().equals(categoryId))
                    .toList();
        }

        // Apply game mode filter
        if (gameMode != null) {
            userGames = userGames.stream()
                    .filter(g -> mapFromEntityGameMode(g.getGameMode()).equals(gameMode))
                    .toList();
        }

        // Calculate stats from filtered games
        return calculateStatsFromGames(user, userGames);
    }

    private com.mabawa.triviacrave.game.entity.UserStats calculateStatsFromGames(User user, List<Game> games) {
        com.mabawa.triviacrave.game.entity.UserStats.UserStatsBuilder builder = com.mabawa.triviacrave.game.entity.UserStats.builder()
                .id(IDGenerator.generateId())
                .user(user);

        int totalGames = games.size();
        int completedGames = (int) games.stream().filter(g -> g.getStatus() == com.mabawa.triviacrave.game.entity.Game.GameStatus.COMPLETED).count();
        int totalQuestions = games.stream().mapToInt(Game::getQuestionsAnswered).sum();
        int totalCorrect = games.stream().mapToInt(Game::getCorrectAnswers).sum();
        long totalScore = games.stream().mapToLong(g -> g.getScore().longValue()).sum();

        builder.totalGamesPlayed(totalGames)
               .gamesCompleted(completedGames)
               .totalQuestionsAnswered(totalQuestions)
               .totalCorrectAnswers(totalCorrect)
               .totalScore(totalScore);

        if (totalGames > 0) {
            builder.averageScore((double) totalScore / totalGames);
            
            OptionalInt maxScore = games.stream().mapToInt(Game::getScore).max();
            if (maxScore.isPresent()) {
                builder.highestScore(maxScore.getAsInt());
            }
        }

        if (totalQuestions > 0) {
            builder.accuracyPercentage((double) totalCorrect / totalQuestions * 100.0);
        }

        return builder.build();
    }

    private Leaderboard buildLeaderboard(LeaderboardCmd cmd) {
        List<com.mabawa.triviacrave.game.entity.UserStats> allStats = userStatsRepository.findAll();
        
        // Apply time range filter if specified
        if (cmd.getTimeRange() != null && cmd.getTimeRange() != TimeRange.ALL_TIME) {
            LocalDateTime cutoff = getTimeRangeCutoff(cmd.getTimeRange());
            allStats = allStats.stream()
                    .filter(stats -> stats.getLastPlayedAt() != null && stats.getLastPlayedAt().isAfter(cutoff))
                    .toList();
        }

        // Apply category filter if specified
        if (cmd.getCategoryId() != null) {
            allStats = allStats.stream()
                    .filter(stats -> stats.getCategory().getId().equals(cmd.getCategoryId()))
                    .toList();
        }

        // Sort by leaderboard type
        Comparator<com.mabawa.triviacrave.game.entity.UserStats> comparator = getLeaderboardComparator(cmd.getType());
        allStats.sort(comparator);

        // Apply limit
        int limit = cmd.getLimit() != null ? cmd.getLimit() : 50;
        allStats = allStats.stream().limit(limit).toList();

        // Build leaderboard entries
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < allStats.size(); i++) {
            com.mabawa.triviacrave.game.entity.UserStats stats = allStats.get(i);
            LeaderboardEntry entry = LeaderboardEntry.newBuilder()
                    .rank(i + 1)
                    .userId(stats.getUser().getId())
                    .score(stats.getTotalScore())
                    .value(getLeaderboardValue(stats, cmd.getType()).floatValue())
                    .gamesPlayed(stats.getTotalGamesPlayed())
                    .achievedAt(stats.getLastPlayedAt() != null ? stats.getLastPlayedAt() : stats.getUpdatedAt())
                    .build();
            entries.add(entry);
        }

        return Leaderboard.newBuilder()
                .type(cmd.getType())
                .timeRange(cmd.getTimeRange() != null ? cmd.getTimeRange() : TimeRange.ALL_TIME)
                .categoryId(cmd.getCategoryId())
                .gameMode(cmd.getGameMode())
                .totalEntries(entries.size())
                .entries(entries)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private GlobalStats buildGlobalStats() {
        long totalUsers = userRepository.countAll();
        long totalGames = gameRepository.countAll();
        long totalQuestions = questionRepository.countAll();
        long totalCategories = categoryRepository.countAll();

        List<Game> allGames = gameRepository.findAll();
        double averageScore = allGames.stream()
                .filter(g -> g.getStatus() == com.mabawa.triviacrave.game.entity.Game.GameStatus.COMPLETED)
                .mapToDouble(g -> g.getScore().doubleValue())
                .average()
                .orElse(0.0);

        // Find most popular category
        Map<Category, Long> categoryCount = allGames.stream()
                .filter(g -> g.getCategory() != null)
                .collect(java.util.stream.Collectors.groupingBy(Game::getCategory, java.util.stream.Collectors.counting()));
        
        Category mostPopularCategory = categoryCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        return GlobalStats.newBuilder()
                .totalUsers((int) totalUsers)
                .totalGames((int) totalGames)
                .totalQuestions((int) totalQuestions)
                .totalCategories((int) totalCategories)
                .averageScorePerGame((float) averageScore)
                .mostPopularCategory(mostPopularCategory != null ? mapCategoryToGraphQL(mostPopularCategory) : null)
                .mostActiveDifficulty(Difficulty.MEDIUM) // Default or calculate from data
                .mostPlayedGameMode(GameMode.SINGLE_PLAYER) // Default or calculate from data
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private List<Achievement> buildUserAchievements(User user) {
        List<Achievement> achievements = new ArrayList<>();
        
        List<Game> userGames = gameRepository.findGamesByUserId(user.getId());
        List<com.mabawa.triviacrave.game.entity.UserStats> userStats = userStatsRepository.findByUserId(user.getId());

        // First Game Achievement
        if (!userGames.isEmpty()) {
            achievements.add(Achievement.newBuilder()
                    .id(IDGenerator.generateId())
                    .userId(user.getId())
                    .type(AchievementType.FIRST_GAME)
                    .name("Welcome to Trivia!")
                    .description("Played your first trivia game")
                    .points(10)
                    .unlockedAt(userGames.get(0).getStartedAt())
                    .build());
        }

        // Perfect Score Achievement
        boolean hasPerfectScore = userGames.stream()
                .anyMatch(g -> g.getAccuracyPercentage() == 100.0);
        if (hasPerfectScore) {
            achievements.add(Achievement.newBuilder()
                    .id(IDGenerator.generateId())
                    .userId(user.getId())
                    .type(AchievementType.PERFECT_SCORE)
                    .name("Perfectionist")
                    .description("Achieved 100% accuracy in a game")
                    .points(50)
                    .unlockedAt(LocalDateTime.now()) // Would be actual unlock time
                    .build());
        }

        // Add more achievements based on user stats...

        return achievements;
    }

    private List<Achievement> calculateEarnedAchievements(User user, Game game) {
        List<Achievement> newAchievements = new ArrayList<>();

        // Check for various achievement conditions
        if (game.getAccuracyPercentage() == 100.0) {
            newAchievements.add(createAchievement(user, AchievementType.PERFECT_SCORE, 
                    "Perfectionist", "Achieved 100% accuracy", 50));
        }

        if (game.getDurationSeconds() != null && game.getDurationSeconds() < 60) {
            newAchievements.add(createAchievement(user, AchievementType.SPEED_DEMON,
                    "Speed Demon", "Completed a game in under 60 seconds", 30));
        }

        return newAchievements;
    }

    private Achievement createAchievement(User user, AchievementType type, String name, String description, int points) {
        return Achievement.newBuilder()
                .id(IDGenerator.generateId())
                .userId(user.getId())
                .type(type)
                .name(name)
                .description(description)
                .points(points)
                .unlockedAt(LocalDateTime.now())
                .build();
    }

    private StatsComparison buildStatsComparison(User user1, User user2, Long categoryId, GameMode gameMode) {
        com.mabawa.triviacrave.game.entity.UserStats stats1 = buildUserStats(user1, categoryId, gameMode, TimeRange.ALL_TIME);
        com.mabawa.triviacrave.game.entity.UserStats stats2 = buildUserStats(user2, categoryId, gameMode, TimeRange.ALL_TIME);

        List<MetricComparison> metrics = buildMetricComparisons(stats1, stats2);
        
        // Determine winner based on total score
        ComparisonResult winner = stats1.getTotalScore() > stats2.getTotalScore() ?
                ComparisonResult.newBuilder()
                        .userId(user1.getId())
                        .totalWins(1)
                        .build() :
                ComparisonResult.newBuilder()
                        .userId(user2.getId())
                        .totalWins(1)
                        .build();

        return StatsComparison.newBuilder()
                .user1(mapToGraphQLUserStats(stats1, user1))
                .user2(mapToGraphQLUserStats(stats2, user2))
                .winner(winner)
                .metrics(metrics)
                .build();
    }

    private List<MetricComparison> buildMetricComparisons(com.mabawa.triviacrave.game.entity.UserStats stats1, com.mabawa.triviacrave.game.entity.UserStats stats2) {
        List<MetricComparison> comparisons = new ArrayList<>();

        comparisons.add(MetricComparison.newBuilder()
                .metric("Total Score")
                .user1Value(stats1.getTotalScore().floatValue())
                .user2Value(stats2.getTotalScore().floatValue())
                .winner(stats1.getTotalScore() > stats2.getTotalScore() ? stats1.getUser().getId() : stats2.getUser().getId())
                .difference(Math.abs(stats1.getTotalScore().floatValue() - stats2.getTotalScore().floatValue()))
                .build());

        comparisons.add(MetricComparison.newBuilder()
                .metric("Accuracy")
                .user1Value(stats1.getAccuracyPercentage().floatValue())
                .user2Value(stats2.getAccuracyPercentage().floatValue())
                .winner(stats1.getAccuracyPercentage() > stats2.getAccuracyPercentage() ? stats1.getUser().getId() : stats2.getUser().getId())
                .difference(Math.abs(stats1.getAccuracyPercentage().floatValue() - stats2.getAccuracyPercentage().floatValue()))
                .build());

        return comparisons;
    }

    // Helper methods for time ranges, comparators, etc.
    
    private LocalDateTime getTimeRangeCutoff(TimeRange timeRange) {
        LocalDateTime now = LocalDateTime.now();
        switch (timeRange) {
            case TODAY:
                return now.truncatedTo(ChronoUnit.DAYS);
            case THIS_WEEK:
                return now.minusWeeks(1);
            case THIS_MONTH:
                return now.minusMonths(1);
            case THIS_YEAR:
                return now.minusYears(1);
            case LAST_7_DAYS:
                return now.minusDays(7);
            case LAST_30_DAYS:
                return now.minusDays(30);
            default:
                return LocalDateTime.MIN;
        }
    }

    private Comparator<com.mabawa.triviacrave.game.entity.UserStats> getLeaderboardComparator(LeaderboardType type) {
        switch (type) {
            case OVERALL_SCORE:
                return (a, b) -> Long.compare(b.getTotalScore(), a.getTotalScore());
            case ACCURACY_RATE:
                return (a, b) -> Double.compare(b.getAccuracyPercentage(), a.getAccuracyPercentage());
            case GAMES_WON:
                return (a, b) -> Integer.compare(b.getGamesCompleted(), a.getGamesCompleted());
            default:
                return (a, b) -> Long.compare(b.getTotalScore(), a.getTotalScore());
        }
    }

    private Double getLeaderboardValue(com.mabawa.triviacrave.game.entity.UserStats stats, LeaderboardType type) {
        switch (type) {
            case OVERALL_SCORE:
                return stats.getTotalScore().doubleValue();
            case ACCURACY_RATE:
                return stats.getAccuracyPercentage();
            case GAMES_WON:
                return stats.getGamesCompleted().doubleValue();
            default:
                return stats.getTotalScore().doubleValue();
        }
    }

    private GameMode mapFromEntityGameMode(Game.GameMode entityMode) {
        switch (entityMode) {
            case QUICK_PLAY:
                return GameMode.SINGLE_PLAYER;
            case TIMED_CHALLENGE:
                return GameMode.MULTIPLAYER;
            case CUSTOM:
                return GameMode.PRACTICE;
            case SURVIVAL:
                return GameMode.TOURNAMENT;
            default:
                return GameMode.SINGLE_PLAYER;
        }
    }

    private com.mabawa.triviacrave.generated.graphql.types.Category mapCategoryToGraphQL(Category category) {
        return com.mabawa.triviacrave.generated.graphql.types.Category.newBuilder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .isActive(category.isActive())
                .questionCount(category.getActiveQuestionCount())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    private com.mabawa.triviacrave.generated.graphql.types.UserStats mapToGraphQLUserStats(com.mabawa.triviacrave.game.entity.UserStats stats, User user) {
        return com.mabawa.triviacrave.generated.graphql.types.UserStats.newBuilder()
                .userId(user.getId())
                .totalGames(stats.getTotalGamesPlayed())
                .completedGames(stats.getGamesCompleted())
                .abandonedGames(stats.getTotalGamesPlayed() - stats.getGamesCompleted())
                .totalScore(stats.getTotalScore())
                .averageScore(stats.getAverageScore().floatValue())
                .totalCorrectAnswers(stats.getTotalCorrectAnswers())
                .totalIncorrectAnswers(stats.getTotalQuestionsAnswered() - stats.getTotalCorrectAnswers())
                .accuracyRate(stats.getAccuracyPercentage().floatValue())
                .averageTimePerQuestion(0.0f) // Would need to calculate from games
                .fastestGameTime(stats.getFastestCompletionSeconds() != null ? stats.getFastestCompletionSeconds().longValue() : null)
                .slowestGameTime(null) // Would need to track this
                .favoriteCategory(stats.getCategory() != null ? mapCategoryToGraphQL(stats.getCategory()) : null)
                .categoryStats(List.of()) // Would build from user's category stats
                .gameModeStats(List.of()) // Would build from user's game mode stats
                .difficultyStats(List.of()) // Would build from user's difficulty stats
                .recentAchievements(List.of()) // Would get recent achievements
                .rank(null) // Would calculate rank
                .createdAt(stats.getCreatedAt())
                .updatedAt(stats.getUpdatedAt())
                .build();
    }
    // Interface methods that return direct types for GraphQL queries
    
    @Override
    public com.mabawa.triviacrave.generated.graphql.types.UserStats getUserStats(UserStatsCmd cmd) {
        validateUserStatsCmd(cmd);
        
        User user = userRepository.findOne(cmd.getUserId());
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        
        com.mabawa.triviacrave.game.entity.UserStats stats = buildUserStats(user, cmd.getCategoryId(), cmd.getGameMode(), cmd.getTimeRange());
        return mapToGraphQLUserStats(stats, user);
    }
    
    @Override
    public com.mabawa.triviacrave.generated.graphql.types.UserStats getMyStats(Long categoryId, GameMode gameMode, TimeRange timeRange) {
        // Get current user from security context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new RuntimeException("User not authenticated");
        }
        
        // Assuming the principal has an id field - may need adjustment based on your auth implementation
        Long userId;
        try {
            // This might need to be adjusted based on how your authentication principal is structured
            userId = Long.valueOf(auth.getName()); // or however you extract user ID from principal
        } catch (Exception e) {
            throw new RuntimeException("Unable to extract user ID from authentication context");
        }
        
        User user = userRepository.findOne(userId);
        if (user == null) {
            throw new RuntimeException("Current user not found");
        }
        
        com.mabawa.triviacrave.game.entity.UserStats stats = buildUserStats(user, categoryId, gameMode, timeRange);
        return mapToGraphQLUserStats(stats, user);
    }
    
    @Override
    public Leaderboard getLeaderboard(LeaderboardCmd cmd) {
        validateLeaderboardCmd(cmd);
        return buildLeaderboard(cmd);
    }
    
    @Override
    public java.util.List<Leaderboard> getLeaderboards(java.util.List<LeaderboardType> types, TimeRange timeRange, Integer limit) {
        if (types == null || types.isEmpty()) {
            throw new RuntimeException("Leaderboard types are required");
        }

        List<Leaderboard> leaderboards = new ArrayList<>();

        for (LeaderboardType type : types) {
            LeaderboardCmd cmd = LeaderboardCmd.newBuilder()
                    .type(type)
                    .timeRange(timeRange != null ? timeRange : TimeRange.ALL_TIME)
                    .limit(limit)
                    .build();
            
            Leaderboard leaderboard = buildLeaderboard(cmd);
            leaderboards.add(leaderboard);
        }
        
        return leaderboards;
    }
    
    @Override
    public GlobalStats getGlobalStats() {
        return buildGlobalStats();
    }
    
    @Override
    public java.util.List<Achievement> getUserAchievements(Long userId) {
        if (userId == null || userId <= 0) {
            throw new RuntimeException("User ID is required");
        }

        User user = userRepository.findOne(userId);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        return buildUserAchievements(user);
    }
    
    @Override
    public java.util.List<Achievement> getMyAchievements() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new RuntimeException("User not authenticated");
        }
        
        Long userId;
        try {
            userId = Long.valueOf(auth.getName());
        } catch (Exception e) {
            throw new RuntimeException("Unable to extract user ID from authentication context");
        }
        
        return getUserAchievements(userId);
    }
    
    @Override
    public StatsComparison compareStats(Long userId1, Long userId2, Long categoryId, GameMode gameMode) {
        if (userId1 == null || userId2 == null) {
            throw new RuntimeException("Both user IDs are required");
        }

        User user1 = userRepository.findOne(userId1);
        User user2 = userRepository.findOne(userId2);

        if (user1 == null || user2 == null) {
            throw new RuntimeException("One or both users not found");
        }

        return buildStatsComparison(user1, user2, categoryId, gameMode);
    }
}