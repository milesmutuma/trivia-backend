package com.mabawa.triviacrave.game.repository;

import com.mabawa.triviacrave.common.repository.AbstractEntityRepo;
import com.mabawa.triviacrave.game.entity.Category;
import com.mabawa.triviacrave.game.entity.Question;
import com.mabawa.triviacrave.game.entity.Question.Difficulty;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
public class QuestionRepository extends AbstractEntityRepo<Question, Long> {
    private static final String CATEGORY_FIELD = "category";
    private static final String DIFFICULTY_FIELD = "difficulty";
    private static final String ACTIVE_FIELD = "active";

    @Autowired
    public QuestionRepository(EntityManager entityManager) {
        super(entityManager, () -> entityManager.getCriteriaBuilder().createQuery(Question.class));
    }

    /**
     * Find all questions by category
     */
    public List<Question> findByCategory(Category category) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Question> query = querySupplier.get();
            Root<Question> root = query.from(Question.class);
            
            query.where(
                cb.and(
                    cb.equal(root.get(CATEGORY_FIELD), category),
                    cb.equal(root.get(ACTIVE_FIELD), true)
                )
            );
            
            TypedQuery<Question> typedQuery = entityManager.createQuery(query);
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding questions by category {}: {}", category.getName(), e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find questions by category and difficulty
     */
    public List<Question> findByCategoryAndDifficulty(Category category, Difficulty difficulty) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Question> query = querySupplier.get();
            Root<Question> root = query.from(Question.class);
            
            query.where(
                cb.and(
                    cb.equal(root.get(CATEGORY_FIELD), category),
                    cb.equal(root.get(DIFFICULTY_FIELD), difficulty),
                    cb.equal(root.get(ACTIVE_FIELD), true)
                )
            );
            
            TypedQuery<Question> typedQuery = entityManager.createQuery(query);
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding questions by category {} and difficulty {}: {}", 
                     category.getName(), difficulty, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Get random questions from a category with specified difficulty
     */
    public List<Question> getRandomQuestions(Category category, Difficulty difficulty, int limit) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Question> query = querySupplier.get();
            Root<Question> root = query.from(Question.class);
            
            query.where(
                cb.and(
                    cb.equal(root.get(CATEGORY_FIELD), category),
                    cb.equal(root.get(DIFFICULTY_FIELD), difficulty),
                    cb.equal(root.get(ACTIVE_FIELD), true)
                )
            );
            
            // Order by random - using database-specific function
            query.orderBy(cb.desc(cb.function("RANDOM", Double.class)));
            
            TypedQuery<Question> typedQuery = entityManager.createQuery(query);
            typedQuery.setMaxResults(limit);
            
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error getting random questions for category {} and difficulty {}: {}", 
                     category.getName(), difficulty, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Get random questions from any category with specified difficulty
     */
    public List<Question> getRandomQuestions(Difficulty difficulty, int limit) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Question> query = querySupplier.get();
            Root<Question> root = query.from(Question.class);
            
            query.where(
                cb.and(
                    cb.equal(root.get(DIFFICULTY_FIELD), difficulty),
                    cb.equal(root.get(ACTIVE_FIELD), true)
                )
            );
            
            // Order by random
            query.orderBy(cb.desc(cb.function("RANDOM", Double.class)));
            
            TypedQuery<Question> typedQuery = entityManager.createQuery(query);
            typedQuery.setMaxResults(limit);
            
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error getting random questions for difficulty {}: {}", difficulty, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Count questions by category
     */
    public long countByCategory(Category category) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> query = cb.createQuery(Long.class);
            Root<Question> root = query.from(Question.class);
            
            query.select(cb.count(root));
            query.where(
                cb.and(
                    cb.equal(root.get(CATEGORY_FIELD), category),
                    cb.equal(root.get(ACTIVE_FIELD), true)
                )
            );
            
            TypedQuery<Long> typedQuery = entityManager.createQuery(query);
            return typedQuery.getSingleResult();
        } catch (Exception e) {
            log.error("Error counting questions by category {}: {}", category.getName(), e.getMessage(), e);
            return 0L;
        }
    }

    /**
     * Count questions by category and difficulty
     */
    public long countByCategoryAndDifficulty(Category category, Difficulty difficulty) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> query = cb.createQuery(Long.class);
            Root<Question> root = query.from(Question.class);
            
            query.select(cb.count(root));
            query.where(
                cb.and(
                    cb.equal(root.get(CATEGORY_FIELD), category),
                    cb.equal(root.get(DIFFICULTY_FIELD), difficulty),
                    cb.equal(root.get(ACTIVE_FIELD), true)
                )
            );
            
            TypedQuery<Long> typedQuery = entityManager.createQuery(query);
            return typedQuery.getSingleResult();
        } catch (Exception e) {
            log.error("Error counting questions by category {} and difficulty {}: {}", 
                     category.getName(), difficulty, e.getMessage(), e);
            return 0L;
        }
    }

    /**
     * Find questions by difficulty level
     */
    public List<Question> findByDifficulty(Difficulty difficulty) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Question> query = querySupplier.get();
            Root<Question> root = query.from(Question.class);
            
            query.where(
                cb.and(
                    cb.equal(root.get(DIFFICULTY_FIELD), difficulty),
                    cb.equal(root.get(ACTIVE_FIELD), true)
                )
            );
            
            TypedQuery<Question> typedQuery = entityManager.createQuery(query);
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding questions by difficulty {}: {}", difficulty, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find active questions with pagination
     */
    public List<Question> findActiveQuestions(int page, int size) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Question> query = querySupplier.get();
            Root<Question> root = query.from(Question.class);
            
            query.where(cb.equal(root.get(ACTIVE_FIELD), true));
            query.orderBy(cb.asc(root.get("id")));
            
            TypedQuery<Question> typedQuery = entityManager.createQuery(query);
            typedQuery.setFirstResult(page * size);
            typedQuery.setMaxResults(size);
            
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding active questions (page {}, size {}): {}", page, size, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find all active questions without pagination
     */
    public List<Question> findActiveQuestions() {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Question> query = querySupplier.get();
            Root<Question> root = query.from(Question.class);
            
            query.where(cb.equal(root.get(ACTIVE_FIELD), true));
            query.orderBy(cb.asc(root.get("id")));
            
            TypedQuery<Question> typedQuery = entityManager.createQuery(query);
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding all active questions: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    protected Class<Question> getEntityClass() {
        return Question.class;
    }
}