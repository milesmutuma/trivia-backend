package com.mabawa.triviacrave.game.service.impl;

import com.mabawa.triviacrave.game.service.ScoreService;
import com.mabawa.triviacrave.generated.graphql.types.Difficulty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ScoreServiceImpl implements ScoreService {
    
    // Base score constants
    private static final int EASY_BASE_SCORE = 10;
    private static final int MEDIUM_BASE_SCORE = 20;
    private static final int HARD_BASE_SCORE = 30;
    
    // Multiplier constants
    private static final double EASY_MULTIPLIER = 1.0;
    private static final double MEDIUM_MULTIPLIER = 1.5;
    private static final double HARD_MULTIPLIER = 2.0;
    
    // Time bonus constants
    private static final double MAX_TIME_BONUS_PERCENTAGE = 0.5; // 50% of base score
    private static final double MIN_TIME_BONUS_THRESHOLD = 0.2; // 20% of time limit
    
    // Streak bonus constants
    private static final int STREAK_THRESHOLD = 3; // Minimum streak to get bonus
    private static final int BASE_STREAK_BONUS = 5;
    private static final int MAX_STREAK_BONUS = 50;
    
    // Completion bonus constants
    private static final int PERFECT_COMPLETION_BONUS = 100;
    private static final double ACCURACY_THRESHOLD = 0.8; // 80% accuracy for accuracy bonus
    private static final int HIGH_ACCURACY_BONUS = 50;

    @Override
    public int calculateBaseScore(Difficulty difficulty, boolean isCorrect) {
        if (!isCorrect) {
            return 0;
        }
        
        return switch (difficulty) {
            case EASY -> EASY_BASE_SCORE;
            case MEDIUM -> MEDIUM_BASE_SCORE;
            case HARD -> HARD_BASE_SCORE;
            default -> {
                log.warn("Unknown difficulty level: {}. Using EASY base score.", difficulty);
                yield EASY_BASE_SCORE;
            }
        };
    }

    @Override
    public int calculateTimeBonus(int timeSpent, int timeLimit) {
        if (timeSpent <= 0 || timeLimit <= 0 || timeSpent >= timeLimit) {
            return 0;
        }
        
        // Calculate time bonus based on how quickly the question was answered
        double timeRatio = (double) timeSpent / timeLimit;
        
        // Only give bonus if answered within the threshold (e.g., within 20% of time limit)
        if (timeRatio < MIN_TIME_BONUS_THRESHOLD) {
            // Maximum bonus for very fast answers
            return (int) (EASY_BASE_SCORE * MAX_TIME_BONUS_PERCENTAGE);
        } else if (timeRatio < 0.5) {
            // Scaled bonus for moderately fast answers
            double bonusRatio = (0.5 - timeRatio) / 0.3; // Scale between 0.2 and 0.5
            return (int) (EASY_BASE_SCORE * MAX_TIME_BONUS_PERCENTAGE * bonusRatio);
        }
        
        return 0; // No bonus for slow answers
    }

    @Override
    public int calculateDifficultyMultiplier(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> (int) EASY_MULTIPLIER;
            case MEDIUM -> (int) MEDIUM_MULTIPLIER;
            case HARD -> (int) HARD_MULTIPLIER;
            default -> {
                log.warn("Unknown difficulty level: {}. Using EASY multiplier.", difficulty);
                yield (int) EASY_MULTIPLIER;
            }
        };
    }

    @Override
    public int calculateStreakBonus(int currentStreak) {
        if (currentStreak < STREAK_THRESHOLD) {
            return 0;
        }
        
        // Progressive streak bonus: more points for longer streaks
        int streakBonus = BASE_STREAK_BONUS * (currentStreak - STREAK_THRESHOLD + 1);
        
        // Cap the streak bonus to prevent it from becoming too large
        return Math.min(streakBonus, MAX_STREAK_BONUS);
    }

    @Override
    public int calculateTotalScore(Difficulty difficulty, boolean isCorrect, int timeSpent, int timeLimit, int currentStreak) {
        if (!isCorrect) {
            return 0;
        }
        
        int baseScore = calculateBaseScore(difficulty, isCorrect);
        int timeBonus = calculateTimeBonus(timeSpent, timeLimit);
        int streakBonus = calculateStreakBonus(currentStreak);
        
        // Apply difficulty multiplier to base score and time bonus
        double multiplier = getDifficultyMultiplier(difficulty);
        int adjustedBaseScore = (int) (baseScore * multiplier);
        int adjustedTimeBonus = (int) (timeBonus * multiplier);
        
        int totalScore = adjustedBaseScore + adjustedTimeBonus + streakBonus;
        
        log.debug("Score calculation - Difficulty: {}, Base: {}, Time Bonus: {}, Streak Bonus: {}, Total: {}", 
                 difficulty, adjustedBaseScore, adjustedTimeBonus, streakBonus, totalScore);
        
        return totalScore;
    }

    @Override
    public int calculateGameCompletionBonus(int correctAnswers, int totalQuestions, long totalTimeSpent) {
        if (totalQuestions == 0) {
            return 0;
        }
        
        double accuracy = (double) correctAnswers / totalQuestions;
        
        // Perfect score bonus
        if (accuracy == 1.0) {
            return PERFECT_COMPLETION_BONUS;
        }
        
        // High accuracy bonus
        if (accuracy >= ACCURACY_THRESHOLD) {
            return HIGH_ACCURACY_BONUS;
        }
        
        // Scaled completion bonus based on accuracy
        int completionBonus = (int) (accuracy * 30); // Up to 30 points for completion
        
        // Time efficiency bonus for completing game quickly
        if (totalTimeSpent > 0 && totalTimeSpent < (totalQuestions * 30)) { // Less than 30 seconds per question
            completionBonus += 20; // Speed completion bonus
        }
        
        return completionBonus;
    }

    @Override
    public double calculateAccuracyBonus(int correctAnswers, int totalQuestions) {
        if (totalQuestions == 0) {
            return 0.0;
        }
        
        double accuracy = (double) correctAnswers / totalQuestions;
        
        // Perfect accuracy
        if (accuracy == 1.0) {
            return 1.5; // 50% bonus multiplier
        }
        
        // High accuracy (80% or above)
        if (accuracy >= ACCURACY_THRESHOLD) {
            return 1.2; // 20% bonus multiplier
        }
        
        // Good accuracy (60% or above)
        if (accuracy >= 0.6) {
            return 1.1; // 10% bonus multiplier
        }
        
        // No bonus for accuracy below 60%
        return 1.0;
    }

    @Override
    public int calculateFinalGameScore(int baseScore, int completionBonus, double accuracyBonus) {
        // Apply accuracy bonus to base score, then add completion bonus
        int adjustedBaseScore = (int) (baseScore * accuracyBonus);
        int finalScore = adjustedBaseScore + completionBonus;
        
        log.info("Final score calculation - Base: {}, Completion Bonus: {}, Accuracy Bonus: {}x, Final: {}", 
                baseScore, completionBonus, accuracyBonus, finalScore);
        
        return Math.max(0, finalScore); // Ensure non-negative score
    }
    
    // Helper method to get difficulty multiplier as double
    private double getDifficultyMultiplier(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> EASY_MULTIPLIER;
            case MEDIUM -> MEDIUM_MULTIPLIER;
            case HARD -> HARD_MULTIPLIER;
            default -> {
                log.warn("Unknown difficulty level: {}. Using EASY multiplier.", difficulty);
                yield EASY_MULTIPLIER;
            }
        };
    }
    
    // Additional utility methods for advanced scoring
    
    /**
     * Calculate score with dynamic difficulty adjustment based on user performance
     */
    public int calculateAdaptiveScore(Difficulty difficulty, boolean isCorrect, 
                                    int timeSpent, int timeLimit, int currentStreak, 
                                    double recentAccuracy) {
        int baseScore = calculateTotalScore(difficulty, isCorrect, timeSpent, timeLimit, currentStreak);
        
        // Adaptive bonus/penalty based on recent performance
        if (recentAccuracy < 0.5) {
            // Encourage struggling players with bonus points
            baseScore = (int) (baseScore * 1.2);
        } else if (recentAccuracy > 0.9) {
            // Challenge high-performing players with higher standards
            baseScore = (int) (baseScore * 0.9);
        }
        
        return baseScore;
    }
    
    /**
     * Calculate penalty for incorrect answers (used for certain game modes)
     */
    public int calculateIncorrectAnswerPenalty(Difficulty difficulty, int currentScore) {
        int penalty = switch (difficulty) {
            case EASY -> 2;
            case MEDIUM -> 5;
            case HARD -> 10;
            default -> 2;
        };
        
        // Don't let penalty reduce score below zero
        return Math.min(penalty, currentScore);
    }
    
    /**
     * Calculate bonus for consecutive correct answers within time limit
     */
    public int calculateSpeedStreakBonus(int streakCount, long averageTimePerQuestion) {
        if (streakCount < 3 || averageTimePerQuestion > 15000) { // 15 seconds
            return 0;
        }
        
        int bonus = streakCount * 5; // 5 points per question in fast streak
        return Math.min(bonus, 75); // Cap at 75 points
    }
}