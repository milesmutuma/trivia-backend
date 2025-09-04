package com.mabawa.triviacrave.game.entity;

import com.mabawa.triviacrave.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "game_players")
public class GamePlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @Column(name = "is_host", nullable = false)
    private Boolean isHost = false;

    @Builder.Default
    @Column(name = "is_ready", nullable = false)
    private Boolean isReady = false;

    @Column(name = "joined_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) {
            joinedAt = LocalDateTime.now();
        }
    }

    // Utility methods
    public void markReady() {
        this.isReady = true;
    }

    public void markNotReady() {
        this.isReady = false;
    }

    public void leaveGame() {
        this.leftAt = LocalDateTime.now();
    }

    public boolean hasLeft() {
        return leftAt != null;
    }

    public boolean isActivePlayer() {
        return leftAt == null;
    }
}