package com.mabawa.triviacrave.game.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "game_questions", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"game_id", "question_id"}))
public class GameQuestion {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Size(max = 500)
    @Column(name = "user_answer", length = 500)
    private String userAnswer;

    @Builder.Default
    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect = false;

    @PositiveOrZero
    @Builder.Default
    @Column(name = "points_earned", nullable = false)
    private Integer pointsEarned = 0;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @PositiveOrZero
    @Column(name = "time_taken_seconds")
    private Integer timeTakenSeconds;

    @Builder.Default
    @Column(name = "question_order", nullable = false)
    private Integer questionOrder = 1;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Utility methods
    public void answerQuestion(String answer, int timeTaken) {
        this.userAnswer = answer;
        this.answeredAt = LocalDateTime.now();
        this.timeTakenSeconds = timeTaken;
        
        if (question != null) {
            this.isCorrect = question.isCorrectAnswer(answer);
            this.pointsEarned = this.isCorrect ? question.getPointValue() : 0;
        }
    }

    public void answerQuestion(String answer) {
        answerQuestion(answer, 0);
    }

    public boolean hasBeenAnswered() {
        return userAnswer != null && !userAnswer.trim().isEmpty();
    }

    public boolean isAnsweredCorrectly() {
        return hasBeenAnswered() && Boolean.TRUE.equals(isCorrect);
    }

    public String getQuestionText() {
        return question != null ? question.getQuestionText() : "";
    }

    public String getCorrectAnswer() {
        return question != null ? question.getCorrectAnswer() : "";
    }

    public Question.Difficulty getDifficulty() {
        return question != null ? question.getDifficulty() : null;
    }

    public int getMaxPoints() {
        return question != null ? question.getPointValue() : 0;
    }

    // Convenience method for quick scoring
    public void markCorrect() {
        this.isCorrect = true;
        this.pointsEarned = getMaxPoints();
        if (this.answeredAt == null) {
            this.answeredAt = LocalDateTime.now();
        }
    }

    public void markIncorrect() {
        this.isCorrect = false;
        this.pointsEarned = 0;
        if (this.answeredAt == null) {
            this.answeredAt = LocalDateTime.now();
        }
    }

    // For calculating performance metrics
    public double getSpeedBonus(int maxTimeSeconds) {
        if (timeTakenSeconds == null || timeTakenSeconds <= 0 || maxTimeSeconds <= 0) {
            return 0.0;
        }
        
        if (timeTakenSeconds >= maxTimeSeconds) {
            return 0.0;
        }
        
        // Speed bonus: faster answers get more bonus (max 50% of base points)
        double speedRatio = 1.0 - ((double) timeTakenSeconds / maxTimeSeconds);
        return pointsEarned * speedRatio * 0.5;
    }

    public int getTotalPointsWithBonus(int maxTimeSeconds) {
        return (int) (pointsEarned + getSpeedBonus(maxTimeSeconds));
    }

    /**
     * Get the time spent answering this question
     * This method provides compatibility with services expecting getTimeSpent()
     */
    public Integer getTimeSpent() {
        return this.timeTakenSeconds;
    }
}