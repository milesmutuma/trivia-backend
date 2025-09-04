package com.mabawa.triviacrave.game.service;

import com.mabawa.triviacrave.generated.graphql.types.Difficulty;

public interface ScoreService {
    int calculateBaseScore(Difficulty difficulty, boolean isCorrect);
    int calculateTimeBonus(int timeSpent, int timeLimit);
    int calculateDifficultyMultiplier(Difficulty difficulty);
    int calculateStreakBonus(int currentStreak);
    int calculateTotalScore(Difficulty difficulty, boolean isCorrect, int timeSpent, int timeLimit, int currentStreak);
    int calculateGameCompletionBonus(int correctAnswers, int totalQuestions, long totalTimeSpent);
    double calculateAccuracyBonus(int correctAnswers, int totalQuestions);
    int calculateFinalGameScore(int baseScore, int completionBonus, double accuracyBonus);
}