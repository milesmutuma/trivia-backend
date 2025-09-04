package com.mabawa.triviacrave.game.entity;

import com.mabawa.triviacrave.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "games")
public class Game {

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

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "game_mode", nullable = false)
    private GameMode gameMode;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private GameStatus status = GameStatus.WAITING;

    @PositiveOrZero
    @Builder.Default
    @Column(name = "score", nullable = false)
    private Integer score = 0;

    @PositiveOrZero
    @Builder.Default
    @Column(name = "questions_answered", nullable = false)
    private Integer questionsAnswered = 0;

    @PositiveOrZero
    @Builder.Default
    @Column(name = "correct_answers", nullable = false)
    private Integer correctAnswers = 0;

    @Column(name = "started_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Builder.Default
    @Column(name = "max_players", nullable = false)
    private Integer maxPlayers = 1;

    @Builder.Default
    @Column(name = "current_players", nullable = false)
    private Integer currentPlayers = 1;

    @Column(name = "invite_code", length = 8)
    private String inviteCode;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GameQuestion> gameQuestions = new ArrayList<>();

    public enum GameMode {
        QUICK_PLAY("Quick Play", 10),
        TIMED_CHALLENGE("Timed Challenge", 20),
        SURVIVAL("Survival", 50),
        CUSTOM("Custom", 0);

        private final String displayName;
        private final int defaultQuestionCount;

        GameMode(String displayName, int defaultQuestionCount) {
            this.displayName = displayName;
            this.defaultQuestionCount = defaultQuestionCount;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getDefaultQuestionCount() {
            return defaultQuestionCount;
        }

        public boolean isTimedMode() {
            return this == TIMED_CHALLENGE;
        }

        public boolean isSurvivalMode() {
            return this == SURVIVAL;
        }
    }

    public enum GameStatus {
        WAITING("Waiting for Players"),
        IN_PROGRESS("In Progress"),
        COMPLETED("Completed"),
        ABANDONED("Abandoned"),
        PAUSED("Paused");

        private final String displayName;

        GameStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isActive() {
            return this == IN_PROGRESS || this == PAUSED;
        }

        public boolean isWaiting() {
            return this == WAITING;
        }

        public boolean isFinished() {
            return this == COMPLETED || this == ABANDONED;
        }
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if (status == GameStatus.COMPLETED && completedAt == null) {
            completedAt = LocalDateTime.now();
            calculateDuration();
        }
    }

    // Utility methods
    public void completeGame() {
        this.status = GameStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        calculateDuration();
    }

    public void abandonGame() {
        this.status = GameStatus.ABANDONED;
        this.completedAt = LocalDateTime.now();
        calculateDuration();
    }

    public void pauseGame() {
        if (this.status == GameStatus.IN_PROGRESS) {
            this.status = GameStatus.PAUSED;
        }
    }

    public void resumeGame() {
        if (this.status == GameStatus.PAUSED) {
            this.status = GameStatus.IN_PROGRESS;
        }
    }

    private void calculateDuration() {
        if (startedAt != null && completedAt != null) {
            this.durationSeconds = (int) java.time.Duration.between(startedAt, completedAt).getSeconds();
        }
    }

    public double getAccuracyPercentage() {
        if (questionsAnswered == 0) {
            return 0.0;
        }
        return (double) correctAnswers / questionsAnswered * 100.0;
    }

    public boolean isGameFinished() {
        return status != null && status.isFinished();
    }

    public boolean isGameActive() {
        return status != null && status.isActive();
    }

    public boolean isGameWaiting() {
        return status != null && status.isWaiting();
    }

    public void startGame() {
        if (this.status == GameStatus.WAITING) {
            this.status = GameStatus.IN_PROGRESS;
            this.startedAt = LocalDateTime.now();
        }
    }

    public boolean canStart() {
        return status == GameStatus.WAITING && currentPlayers >= 1;
    }

    public boolean isFull() {
        return currentPlayers >= maxPlayers;
    }

    public void addPlayer() {
        if (!isFull()) {
            this.currentPlayers++;
        }
    }

    public void removePlayer() {
        if (this.currentPlayers > 0) {
            this.currentPlayers--;
        }
    }

    public void addGameQuestion(GameQuestion gameQuestion) {
        if (gameQuestions == null) {
            gameQuestions = new ArrayList<>();
        }
        gameQuestions.add(gameQuestion);
        gameQuestion.setGame(this);
    }

    public void removeGameQuestion(GameQuestion gameQuestion) {
        if (gameQuestions != null) {
            gameQuestions.remove(gameQuestion);
            gameQuestion.setGame(null);
        }
    }

    public void incrementScore(int points) {
        this.score += points;
    }

    public void incrementQuestionsAnswered() {
        this.questionsAnswered++;
    }

    public void incrementCorrectAnswers() {
        this.correctAnswers++;
    }

    // Convenience method for answering a question
    public void answerQuestion(boolean isCorrect, int points) {
        incrementQuestionsAnswered();
        if (isCorrect) {
            incrementCorrectAnswers();
            incrementScore(points);
        }
    }

    public int getRemainingQuestions() {
        int totalQuestions = gameMode.getDefaultQuestionCount();
        return Math.max(0, totalQuestions - questionsAnswered);
    }
}