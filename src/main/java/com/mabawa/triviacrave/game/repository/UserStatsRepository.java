package com.mabawa.triviacrave.game.repository;

import com.mabawa.triviacrave.common.repository.AbstractEntityRepo;
import com.mabawa.triviacrave.game.entity.UserStats;
import com.mabawa.triviacrave.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
public class UserStatsRepository extends AbstractEntityRepo<UserStats, Long> {
    private static final String USER_FIELD = "user";
    private static final String TOTAL_GAMES_FIELD = "totalGamesPlayed";
    private static final String TOTAL_SCORE_FIELD = "totalScore";
    private static final String HIGHEST_SCORE_FIELD = "highestScore";
    private static final String AVERAGE_SCORE_FIELD = "averageScore";
    private static final String CORRECT_ANSWERS_FIELD = "totalCorrectAnswers";
    private static final String TOTAL_QUESTIONS_FIELD = "totalQuestionsAnswered";

    @Autowired
    public UserStatsRepository(EntityManager entityManager) {
        super(entityManager, () -> entityManager.getCriteriaBuilder().createQuery(UserStats.class));
    }

    /**
     * Find user statistics by user
     */
    public Optional<UserStats> findByUser(User user) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<UserStats> query = querySupplier.get();
            Root<UserStats> root = query.from(UserStats.class);
            
            query.where(cb.equal(root.get(USER_FIELD), user));
            
            TypedQuery<UserStats> typedQuery = entityManager.createQuery(query);
            List<UserStats> results = typedQuery.getResultList();
            
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.error("Error finding user stats for user {}: {}", user.getId(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Find user statistics by user ID and category ID
     */
    public Optional<UserStats> findByUserIdAndCategoryId(Long userId, Long categoryId) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<UserStats> query = querySupplier.get();
            Root<UserStats> root = query.from(UserStats.class);
            
            query.where(cb.and(
                cb.equal(root.get("user").get("id"), userId),
                cb.equal(root.get("category").get("id"), categoryId)
            ));
            
            TypedQuery<UserStats> typedQuery = entityManager.createQuery(query);
            List<UserStats> results = typedQuery.getResultList();
            
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.error("Error finding user stats for userId {} and categoryId {}: {}", userId, categoryId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Find all user statistics by user ID
     */
    public List<UserStats> findByUserId(Long userId) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<UserStats> query = querySupplier.get();
            Root<UserStats> root = query.from(UserStats.class);
            
            query.where(cb.equal(root.get("user").get("id"), userId));
            
            TypedQuery<UserStats> typedQuery = entityManager.createQuery(query);
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding user stats for userId {}: {}", userId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find or create user statistics
     */
    @Transactional
    public UserStats findOrCreateByUser(User user) {
        Optional<UserStats> existing = findByUser(user);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        UserStats newStats = UserStats.builder()
                .user(user)
                .totalGamesPlayed(0)
                .totalScore(0L)
                .highestScore(0)
                .averageScore(0.0)
                .totalCorrectAnswers(0)
                .totalQuestionsAnswered(0)
                .accuracyPercentage(0.0)
                .build();
        
        return save(newStats);
    }

    /**
     * Update user statistics after a game
     */
    @Transactional
    public UserStats updateStatistics(User user, int gameScore, int correctAnswers, int totalQuestions) {
        try {
            UserStats stats = findOrCreateByUser(user);
            
            // Update totals
            stats.setTotalGamesPlayed(stats.getTotalGamesPlayed() + 1);
            stats.setTotalScore(stats.getTotalScore() + gameScore);
            stats.setTotalCorrectAnswers(stats.getTotalCorrectAnswers() + correctAnswers);
            stats.setTotalQuestionsAnswered(stats.getTotalQuestionsAnswered() + totalQuestions);
            
            // Update highest score if needed
            if (gameScore > stats.getHighestScore()) {
                stats.setHighestScore(gameScore);
            }
            
            // Calculate average score
            double averageScore = (double) stats.getTotalScore() / stats.getTotalGamesPlayed();
            stats.setAverageScore(averageScore);
            
            // Calculate accuracy percentage
            if (stats.getTotalQuestionsAnswered() > 0) {
                double accuracy = ((double) stats.getTotalCorrectAnswers() / stats.getTotalQuestionsAnswered()) * 100;
                stats.setAccuracyPercentage(accuracy);
            }
            
            return save(stats);
        } catch (Exception e) {
            log.error("Error updating statistics for user {}: {}", user.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to update user statistics", e);
        }
    }

    /**
     * Get top players by total score
     */
    public List<UserStats> getTopPlayersByScore(int limit) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<UserStats> query = querySupplier.get();
            Root<UserStats> root = query.from(UserStats.class);
            
            query.where(cb.greaterThan(root.get(TOTAL_GAMES_FIELD), 0));
            query.orderBy(cb.desc(root.get(TOTAL_SCORE_FIELD)));
            
            TypedQuery<UserStats> typedQuery = entityManager.createQuery(query);
            if (limit > 0) {
                typedQuery.setMaxResults(limit);
            }
            
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error getting top players by score: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Get top players by highest score
     */
    public List<UserStats> getTopPlayersByHighestScore(int limit) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<UserStats> query = querySupplier.get();
            Root<UserStats> root = query.from(UserStats.class);
            
            query.where(cb.greaterThan(root.get(TOTAL_GAMES_FIELD), 0));
            query.orderBy(cb.desc(root.get(HIGHEST_SCORE_FIELD)));
            
            TypedQuery<UserStats> typedQuery = entityManager.createQuery(query);
            if (limit > 0) {
                typedQuery.setMaxResults(limit);
            }
            
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error getting top players by highest score: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Get top players by average score (minimum games required)
     */
    public List<UserStats> getTopPlayersByAverageScore(int limit, int minimumGames) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<UserStats> query = querySupplier.get();
            Root<UserStats> root = query.from(UserStats.class);
            
            query.where(cb.greaterThanOrEqualTo(root.get(TOTAL_GAMES_FIELD), minimumGames));
            query.orderBy(cb.desc(root.get(AVERAGE_SCORE_FIELD)));
            
            TypedQuery<UserStats> typedQuery = entityManager.createQuery(query);
            if (limit > 0) {
                typedQuery.setMaxResults(limit);
            }
            
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error getting top players by average score: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Get top players by accuracy percentage (minimum games required)
     */
    public List<UserStats> getTopPlayersByAccuracy(int limit, int minimumGames) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<UserStats> query = querySupplier.get();
            Root<UserStats> root = query.from(UserStats.class);
            
            query.where(cb.greaterThanOrEqualTo(root.get(TOTAL_GAMES_FIELD), minimumGames));
            query.orderBy(cb.desc(root.get("accuracyPercentage")));
            
            TypedQuery<UserStats> typedQuery = entityManager.createQuery(query);
            if (limit > 0) {
                typedQuery.setMaxResults(limit);
            }
            
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error getting top players by accuracy: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Get all users with at least one completed game
     */
    public List<UserStats> findActiveUsers() {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<UserStats> query = querySupplier.get();
            Root<UserStats> root = query.from(UserStats.class);
            
            query.where(cb.greaterThan(root.get(TOTAL_GAMES_FIELD), 0));
            query.orderBy(cb.desc(root.get(TOTAL_SCORE_FIELD)));
            
            TypedQuery<UserStats> typedQuery = entityManager.createQuery(query);
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding active users: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Get user rank by total score
     */
    public long getUserRankByTotalScore(User user) {
        try {
            Optional<UserStats> userStats = findByUser(user);
            if (userStats.isEmpty()) {
                return 0L;
            }
            
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> query = cb.createQuery(Long.class);
            Root<UserStats> root = query.from(UserStats.class);
            
            query.select(cb.count(root));
            query.where(
                cb.and(
                    cb.greaterThan(root.get(TOTAL_GAMES_FIELD), 0),
                    cb.greaterThan(root.get(TOTAL_SCORE_FIELD), userStats.get().getTotalScore())
                )
            );
            
            TypedQuery<Long> typedQuery = entityManager.createQuery(query);
            return typedQuery.getSingleResult() + 1; // +1 because rank is 1-based
        } catch (Exception e) {
            log.error("Error getting user rank for user {}: {}", user.getId(), e.getMessage(), e);
            return 0L;
        }
    }

    /**
     * Reset user statistics
     */
    @Transactional
    public UserStats resetUserStats(User user) {
        try {
            Optional<UserStats> existing = findByUser(user);
            if (existing.isEmpty()) {
                return findOrCreateByUser(user);
            }
            
            UserStats stats = existing.get();
            stats.setTotalGamesPlayed(0);
            stats.setTotalScore(0L);
            stats.setHighestScore(0);
            stats.setAverageScore(0.0);
            stats.setTotalCorrectAnswers(0);
            stats.setTotalQuestionsAnswered(0);
            stats.setAccuracyPercentage(0.0);
            
            return save(stats);
        } catch (Exception e) {
            log.error("Error resetting user stats for user {}: {}", user.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to reset user statistics", e);
        }
    }

    /**
     * Count total users with statistics
     */
    public long countUsersWithStats() {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> query = cb.createQuery(Long.class);
            Root<UserStats> root = query.from(UserStats.class);
            
            query.select(cb.count(root));
            query.where(cb.greaterThan(root.get(TOTAL_GAMES_FIELD), 0));
            
            TypedQuery<Long> typedQuery = entityManager.createQuery(query);
            return typedQuery.getSingleResult();
        } catch (Exception e) {
            log.error("Error counting users with stats: {}", e.getMessage(), e);
            return 0L;
        }
    }

    @Override
    protected Class<UserStats> getEntityClass() {
        return UserStats.class;
    }
}