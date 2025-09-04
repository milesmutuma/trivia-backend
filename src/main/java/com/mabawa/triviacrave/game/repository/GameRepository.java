package com.mabawa.triviacrave.game.repository;

import com.mabawa.triviacrave.common.repository.AbstractEntityRepo;
import com.mabawa.triviacrave.game.entity.Game;
import com.mabawa.triviacrave.game.entity.Game.GameStatus;
import com.mabawa.triviacrave.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
public class GameRepository extends AbstractEntityRepo<Game, Long> {
    private static final String USER_FIELD = "user";
    private static final String STATUS_FIELD = "status";
    private static final String STARTED_AT_FIELD = "startedAt";
    private static final String COMPLETED_AT_FIELD = "completedAt";

    @Autowired
    public GameRepository(EntityManager entityManager) {
        super(entityManager, () -> entityManager.getCriteriaBuilder().createQuery(Game.class));
    }

    /**
     * Find active game by user (game that is in progress)
     */
    public Optional<Game> findActiveGameByUser(User user) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Game> query = querySupplier.get();
            Root<Game> root = query.from(Game.class);
            
            query.where(
                cb.and(
                    cb.equal(root.get(USER_FIELD), user),
                    cb.equal(root.get(STATUS_FIELD), GameStatus.IN_PROGRESS)
                )
            );
            
            query.orderBy(cb.desc(root.get(STARTED_AT_FIELD)));
            
            TypedQuery<Game> typedQuery = entityManager.createQuery(query);
            typedQuery.setMaxResults(1);
            
            List<Game> results = typedQuery.getResultList();
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.error("Error finding active game for user {}: {}", user.getId(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Find games by user ordered by start date (most recent first)
     */
    public List<Game> findByUserOrderByStartedAtDesc(User user, int limit) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Game> query = querySupplier.get();
            Root<Game> root = query.from(Game.class);
            
            query.where(cb.equal(root.get(USER_FIELD), user));
            query.orderBy(cb.desc(root.get(STARTED_AT_FIELD)));
            
            TypedQuery<Game> typedQuery = entityManager.createQuery(query);
            if (limit > 0) {
                typedQuery.setMaxResults(limit);
            }
            
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding games for user {}: {}", user.getId(), e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find all games by user (no limit)
     */
    public List<Game> findByUserOrderByStartedAtDesc(User user) {
        return findByUserOrderByStartedAtDesc(user, 0);
    }

    /**
     * Find games by user ID
     */
    public List<Game> findGamesByUserId(Long userId) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Game> query = querySupplier.get();
            Root<Game> root = query.from(Game.class);
            
            query.where(cb.equal(root.get("user").get("id"), userId));
            query.orderBy(cb.desc(root.get(STARTED_AT_FIELD)));
            
            TypedQuery<Game> typedQuery = entityManager.createQuery(query);
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding games for user ID {}: {}", userId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find completed games by user
     */
    public List<Game> findCompletedGamesByUser(User user) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Game> query = querySupplier.get();
            Root<Game> root = query.from(Game.class);
            
            query.where(
                cb.and(
                    cb.equal(root.get(USER_FIELD), user),
                    cb.equal(root.get(STATUS_FIELD), GameStatus.COMPLETED)
                )
            );
            
            query.orderBy(cb.desc(root.get(COMPLETED_AT_FIELD)));
            
            TypedQuery<Game> typedQuery = entityManager.createQuery(query);
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding completed games for user {}: {}", user.getId(), e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find completed games across all users for leaderboard
     */
    public List<Game> findCompletedGames(int limit) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Game> query = querySupplier.get();
            Root<Game> root = query.from(Game.class);
            
            query.where(cb.equal(root.get(STATUS_FIELD), GameStatus.COMPLETED));
            query.orderBy(
                cb.desc(root.get("score")),
                cb.asc(root.get(COMPLETED_AT_FIELD))
            );
            
            TypedQuery<Game> typedQuery = entityManager.createQuery(query);
            if (limit > 0) {
                typedQuery.setMaxResults(limit);
            }
            
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding completed games: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find games by status
     */
    public List<Game> findByStatus(GameStatus status) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Game> query = querySupplier.get();
            Root<Game> root = query.from(Game.class);
            
            query.where(cb.equal(root.get(STATUS_FIELD), status));
            query.orderBy(cb.desc(root.get(STARTED_AT_FIELD)));
            
            TypedQuery<Game> typedQuery = entityManager.createQuery(query);
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding games by status {}: {}", status, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find games started within a date range
     */
    public List<Game> findGamesInDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Game> query = querySupplier.get();
            Root<Game> root = query.from(Game.class);
            
            query.where(
                cb.between(root.get(STARTED_AT_FIELD), startDate, endDate)
            );
            
            query.orderBy(cb.desc(root.get(STARTED_AT_FIELD)));
            
            TypedQuery<Game> typedQuery = entityManager.createQuery(query);
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding games in date range {} to {}: {}", 
                     startDate, endDate, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Count completed games by user
     */
    public long countCompletedGamesByUser(User user) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> query = cb.createQuery(Long.class);
            Root<Game> root = query.from(Game.class);
            
            query.select(cb.count(root));
            query.where(
                cb.and(
                    cb.equal(root.get(USER_FIELD), user),
                    cb.equal(root.get(STATUS_FIELD), GameStatus.COMPLETED)
                )
            );
            
            TypedQuery<Long> typedQuery = entityManager.createQuery(query);
            return typedQuery.getSingleResult();
        } catch (Exception e) {
            log.error("Error counting completed games for user {}: {}", user.getId(), e.getMessage(), e);
            return 0L;
        }
    }

    /**
     * Find user's highest scoring game
     */
    public Optional<Game> findHighestScoringGameByUser(User user) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Game> query = querySupplier.get();
            Root<Game> root = query.from(Game.class);
            
            query.where(
                cb.and(
                    cb.equal(root.get(USER_FIELD), user),
                    cb.equal(root.get(STATUS_FIELD), GameStatus.COMPLETED)
                )
            );
            
            query.orderBy(cb.desc(root.get("score")));
            
            TypedQuery<Game> typedQuery = entityManager.createQuery(query);
            typedQuery.setMaxResults(1);
            
            List<Game> results = typedQuery.getResultList();
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.error("Error finding highest scoring game for user {}: {}", user.getId(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Find recent games (last N days)
     */
    public List<Game> findRecentGames(int days, int limit) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Game> query = querySupplier.get();
            Root<Game> root = query.from(Game.class);
            
            query.where(cb.greaterThanOrEqualTo(root.get(STARTED_AT_FIELD), cutoffDate));
            query.orderBy(cb.desc(root.get(STARTED_AT_FIELD)));
            
            TypedQuery<Game> typedQuery = entityManager.createQuery(query);
            if (limit > 0) {
                typedQuery.setMaxResults(limit);
            }
            
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding recent games (last {} days): {}", days, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find active games by user ID
     */
    public List<Game> findActiveGamesByUserId(Long userId) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Game> query = querySupplier.get();
            Root<Game> root = query.from(Game.class);
            
            query.where(
                cb.and(
                    cb.equal(root.get("user").get("id"), userId),
                    cb.equal(root.get(STATUS_FIELD), GameStatus.IN_PROGRESS)
                )
            );
            
            query.orderBy(cb.desc(root.get(STARTED_AT_FIELD)));
            
            TypedQuery<Game> typedQuery = entityManager.createQuery(query);
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding active games for user ID {}: {}", userId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find games by game mode
     */
    public List<Game> findGamesByMode(Game.GameMode gameMode) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Game> query = querySupplier.get();
            Root<Game> root = query.from(Game.class);
            
            query.where(cb.equal(root.get("gameMode"), gameMode));
            query.orderBy(cb.desc(root.get(STARTED_AT_FIELD)));
            
            TypedQuery<Game> typedQuery = entityManager.createQuery(query);
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding games by mode {}: {}", gameMode, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find games by user ID and status
     */
    public List<Game> findByUserIdAndStatus(Long userId, GameStatus status) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Game> query = querySupplier.get();
            Root<Game> root = query.from(Game.class);
            
            query.where(
                cb.and(
                    cb.equal(root.get("user").get("id"), userId),
                    cb.equal(root.get(STATUS_FIELD), status)
                )
            );
            
            query.orderBy(cb.desc(root.get(STARTED_AT_FIELD)));
            
            TypedQuery<Game> typedQuery = entityManager.createQuery(query);
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding games for user ID {} and status {}: {}", userId, status, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find games by user ID (alias for findGamesByUserId)
     */
    public List<Game> findByUserId(Long userId) {
        return findGamesByUserId(userId);
    }

    /**
     * Find games by mode (alias for findGamesByMode)
     */
    public List<Game> findByMode(Game.GameMode gameMode) {
        return findGamesByMode(gameMode);
    }

    /**
     * Find game by invite code
     */
    public Game findByInviteCode(String inviteCode) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Game> query = querySupplier.get();
            Root<Game> root = query.from(Game.class);
            
            query.where(cb.equal(root.get("inviteCode"), inviteCode));
            
            TypedQuery<Game> typedQuery = entityManager.createQuery(query);
            typedQuery.setMaxResults(1);
            
            List<Game> results = typedQuery.getResultList();
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            log.error("Error finding game by invite code {}: {}", inviteCode, e.getMessage(), e);
            return null;
        }
    }

    @Override
    protected Class<Game> getEntityClass() {
        return Game.class;
    }
}