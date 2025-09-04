package com.mabawa.triviacrave.game.entity;

import com.mabawa.triviacrave.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_stats", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "category_id"}))
public class UserStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @PositiveOrZero
    @Builder.Default
    @Column(name = "total_games_played", nullable = false)
    private Integer totalGamesPlayed = 0;

    // Alias for repository compatibility
    public Integer getTotalGames() {
        return totalGamesPlayed;
    }

    public void setTotalGames(Integer totalGames) {
        this.totalGamesPlayed = totalGames;
    }

    @PositiveOrZero
    @Builder.Default
    @Column(name = "games_completed", nullable = false)
    private Integer gamesCompleted = 0;

    @PositiveOrZero
    @Builder.Default
    @Column(name = "total_questions_answered", nullable = false)
    private Integer totalQuestionsAnswered = 0;

    // Alias for repository compatibility
    public Integer getTotalQuestions() {
        return totalQuestionsAnswered;
    }

    public void setTotalQuestions(Integer totalQuestions) {
        this.totalQuestionsAnswered = totalQuestions;
    }

    @PositiveOrZero
    @Builder.Default
    @Column(name = "total_correct_answers", nullable = false)
    private Integer totalCorrectAnswers = 0;

    @PositiveOrZero
    @Builder.Default
    @Column(name = "total_score", nullable = false)
    private Long totalScore = 0L;

    @PositiveOrZero
    @Builder.Default
    @Column(name = "highest_score", nullable = false)
    private Integer highestScore = 0;

    @PositiveOrZero
    @Builder.Default
    @Column(name = "current_streak", nullable = false)
    private Integer currentStreak = 0;

    @PositiveOrZero
    @Builder.Default
    @Column(name = "longest_streak", nullable = false)
    private Integer longestStreak = 0;

    @PositiveOrZero
    @Builder.Default
    @Column(name = "average_score", nullable = false)
    private Double averageScore = 0.0;

    @PositiveOrZero
    @Builder.Default
    @Column(name = "accuracy_percentage", nullable = false)
    private Double accuracyPercentage = 0.0;

    @PositiveOrZero
    @Column(name = "fastest_completion_seconds")
    private Integer fastestCompletionSeconds;

    @Column(name = "last_played_at")
    private LocalDateTime lastPlayedAt;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Utility methods for updating stats after a game
    public void updateStatsAfterGame(Game game) {
        if (game == null || !game.isGameFinished()) {
            return;
        }

        // Update basic counters
        totalGamesPlayed++;
        if (game.getStatus() == Game.GameStatus.COMPLETED) {
            gamesCompleted++;
        }

        totalQuestionsAnswered += game.getQuestionsAnswered();
        totalCorrectAnswers += game.getCorrectAnswers();
        totalScore += game.getScore();

        // Update highest score
        if (game.getScore() > highestScore) {
            highestScore = game.getScore();
        }

        // Update fastest completion
        if (game.getStatus() == Game.GameStatus.COMPLETED && 
            game.getDurationSeconds() != null) {
            if (fastestCompletionSeconds == null || 
                game.getDurationSeconds() < fastestCompletionSeconds) {
                fastestCompletionSeconds = game.getDurationSeconds();
            }
        }

        // Update streak
        updateStreak(game.getAccuracyPercentage() >= 70.0); // Consider 70%+ as successful

        // Recalculate derived stats
        recalculateStats();

        lastPlayedAt = LocalDateTime.now();
    }

    private void updateStreak(boolean wasSuccessful) {
        if (wasSuccessful) {
            currentStreak++;
            if (currentStreak > longestStreak) {
                longestStreak = currentStreak;
            }
        } else {
            currentStreak = 0;
        }
    }

    private void recalculateStats() {
        // Recalculate average score
        if (gamesCompleted > 0) {
            averageScore = (double) totalScore / gamesCompleted;
        } else {
            averageScore = 0.0;
        }

        // Recalculate accuracy percentage
        if (totalQuestionsAnswered > 0) {
            accuracyPercentage = (double) totalCorrectAnswers / totalQuestionsAnswered * 100.0;
        } else {
            accuracyPercentage = 0.0;
        }
    }

    // Convenience methods for statistics
    public double getCompletionRate() {
        if (totalGamesPlayed == 0) {
            return 0.0;
        }
        return (double) gamesCompleted / totalGamesPlayed * 100.0;
    }

    public boolean isActivePlayer() {
        if (lastPlayedAt == null) {
            return false;
        }
        return lastPlayedAt.isAfter(LocalDateTime.now().minusDays(7));
    }

    public PlayerLevel getPlayerLevel() {
        if (totalScore < 1000) return PlayerLevel.ROOKIE;
        if (totalScore < 5000) return PlayerLevel.AMATEUR;
        if (totalScore < 15000) return PlayerLevel.INTERMEDIATE;
        if (totalScore < 30000) return PlayerLevel.ADVANCED;
        if (totalScore < 50000) return PlayerLevel.EXPERT;
        return PlayerLevel.MASTER;
    }

    public enum PlayerLevel {
        ROOKIE("Rookie", 0),
        AMATEUR("Amateur", 1000),
        INTERMEDIATE("Intermediate", 5000),
        ADVANCED("Advanced", 15000),
        EXPERT("Expert", 30000),
        MASTER("Master", 50000);

        private final String displayName;
        private final long requiredScore;

        PlayerLevel(String displayName, long requiredScore) {
            this.displayName = displayName;
            this.requiredScore = requiredScore;
        }

        public String getDisplayName() {
            return displayName;
        }

        public long getRequiredScore() {
            return requiredScore;
        }

        public PlayerLevel getNextLevel() {
            PlayerLevel[] levels = PlayerLevel.values();
            int currentIndex = this.ordinal();
            if (currentIndex < levels.length - 1) {
                return levels[currentIndex + 1];
            }
            return this; // Already at max level
        }

        public long getScoreToNextLevel(long currentScore) {
            PlayerLevel nextLevel = getNextLevel();
            if (nextLevel == this) {
                return 0; // Already at max level
            }
            return Math.max(0, nextLevel.getRequiredScore() - currentScore);
        }
    }

    public long getScoreToNextLevel() {
        return getPlayerLevel().getScoreToNextLevel(totalScore);
    }

    public double getProgressToNextLevel() {
        PlayerLevel current = getPlayerLevel();
        PlayerLevel next = current.getNextLevel();
        
        if (next == current) {
            return 100.0; // Already at max level
        }
        
        long progressInCurrentLevel = totalScore - current.getRequiredScore();
        long totalRequiredForNext = next.getRequiredScore() - current.getRequiredScore();
        
        return (double) progressInCurrentLevel / totalRequiredForNext * 100.0;
    }

    // Performance rating (0-100 scale)
    public double getPerformanceRating() {
        double accuracyWeight = 0.4;
        double completionWeight = 0.3;
        double streakWeight = 0.2;
        double activityWeight = 0.1;

        double accuracyScore = Math.min(100.0, accuracyPercentage);
        double completionScore = Math.min(100.0, getCompletionRate());
        double streakScore = Math.min(100.0, longestStreak * 10.0); // 10 points per streak
        double activityScore = isActivePlayer() ? 100.0 : 50.0;

        return (accuracyScore * accuracyWeight) +
               (completionScore * completionWeight) +
               (streakScore * streakWeight) +
               (activityScore * activityWeight);
    }
}