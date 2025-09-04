package com.mabawa.triviacrave.game.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@Table(name = "questions")
public class Question {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @NotBlank
    @Size(max = 1000)
    @Column(name = "question_text", nullable = false, length = 1000)
    private String questionText;

    @NotBlank
    @Size(max = 500)
    @Column(name = "correct_answer", nullable = false, length = 500)
    private String correctAnswer;

    @Size(max = 500)
    @Column(name = "option_a", length = 500)
    private String optionA;

    @Size(max = 500)
    @Column(name = "option_b", length = 500)
    private String optionB;

    @Size(max = 500)
    @Column(name = "option_c", length = 500)
    private String optionC;

    @Size(max = 500)
    @Column(name = "option_d", length = 500)
    private String optionD;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false)
    private Difficulty difficulty;

    @Column(name = "explanation", length = 1000)
    private String explanation;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Builder.Default
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GameQuestion> gameQuestions = new ArrayList<>();

    public enum Difficulty {
        EASY("Easy", 10),
        MEDIUM("Medium", 20),
        HARD("Hard", 30);

        private final String displayName;
        private final int pointValue;

        Difficulty(String displayName, int pointValue) {
            this.displayName = displayName;
            this.pointValue = pointValue;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getPointValue() {
            return pointValue;
        }
    }

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

    // Utility methods
    public boolean hasMultipleChoiceOptions() {
        return optionA != null && optionB != null && 
               optionC != null && optionD != null;
    }

    public List<String> getOptions() {
        return List.of(optionA, optionB, optionC, optionD)
                .stream()
                .filter(option -> option != null && !option.trim().isEmpty())
                .toList();
    }

    public boolean isCorrectAnswer(String answer) {
        return correctAnswer != null && 
               correctAnswer.trim().equalsIgnoreCase(answer.trim());
    }

    public int getPointValue() {
        return difficulty != null ? difficulty.getPointValue() : 0;
    }
}