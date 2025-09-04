package com.mabawa.triviacrave.game.repository;

import com.mabawa.triviacrave.common.repository.AbstractEntityRepo;
import com.mabawa.triviacrave.game.entity.Game;
import com.mabawa.triviacrave.game.entity.GameQuestion;
import com.mabawa.triviacrave.game.entity.Question;
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
public class GameQuestionRepository extends AbstractEntityRepo<GameQuestion, Long> {
    private static final String GAME_FIELD = "game";
    private static final String QUESTION_ORDER_FIELD = "questionOrder";
    private static final String ANSWERED_FIELD = "answered";
    private static final String QUESTION_FIELD = "question";

    @Autowired
    public GameQuestionRepository(EntityManager entityManager) {
        super(entityManager, () -> entityManager.getCriteriaBuilder().createQuery(GameQuestion.class));
    }

    /**
     * Find all game questions for a game ordered by question order
     */
    public List<GameQuestion> findByGameOrderByQuestionOrder(Game game) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<GameQuestion> query = querySupplier.get();
            Root<GameQuestion> root = query.from(GameQuestion.class);
            
            query.where(cb.equal(root.get(GAME_FIELD), game));
            query.orderBy(cb.asc(root.get(QUESTION_ORDER_FIELD)));
            
            TypedQuery<GameQuestion> typedQuery = entityManager.createQuery(query);
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding game questions for game {}: {}", game.getId(), e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find the current (next unanswered) question for a game
     */
    public Optional<GameQuestion> findCurrentQuestion(Game game) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<GameQuestion> query = querySupplier.get();
            Root<GameQuestion> root = query.from(GameQuestion.class);
            
            query.where(
                cb.and(
                    cb.equal(root.get(GAME_FIELD), game),
                    cb.equal(root.get(ANSWERED_FIELD), false)
                )
            );
            
            query.orderBy(cb.asc(root.get(QUESTION_ORDER_FIELD)));
            
            TypedQuery<GameQuestion> typedQuery = entityManager.createQuery(query);
            typedQuery.setMaxResults(1);
            
            List<GameQuestion> results = typedQuery.getResultList();
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.error("Error finding current question for game {}: {}", game.getId(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Find a specific game question by game and question order
     */
    public Optional<GameQuestion> findByGameAndQuestionOrder(Game game, int questionOrder) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<GameQuestion> query = querySupplier.get();
            Root<GameQuestion> root = query.from(GameQuestion.class);
            
            query.where(
                cb.and(
                    cb.equal(root.get(GAME_FIELD), game),
                    cb.equal(root.get(QUESTION_ORDER_FIELD), questionOrder)
                )
            );
            
            TypedQuery<GameQuestion> typedQuery = entityManager.createQuery(query);
            List<GameQuestion> results = typedQuery.getResultList();
            
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.error("Error finding game question for game {} and order {}: {}", 
                     game.getId(), questionOrder, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Find a game question by game and question
     */
    public Optional<GameQuestion> findByGameAndQuestion(Game game, Question question) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<GameQuestion> query = querySupplier.get();
            Root<GameQuestion> root = query.from(GameQuestion.class);
            
            query.where(
                cb.and(
                    cb.equal(root.get(GAME_FIELD), game),
                    cb.equal(root.get(QUESTION_FIELD), question)
                )
            );
            
            TypedQuery<GameQuestion> typedQuery = entityManager.createQuery(query);
            List<GameQuestion> results = typedQuery.getResultList();
            
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.error("Error finding game question for game {} and question {}: {}", 
                     game.getId(), question.getId(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Count answered questions in a game
     */
    public long countAnsweredQuestions(Game game) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> query = cb.createQuery(Long.class);
            Root<GameQuestion> root = query.from(GameQuestion.class);
            
            query.select(cb.count(root));
            query.where(
                cb.and(
                    cb.equal(root.get(GAME_FIELD), game),
                    cb.equal(root.get(ANSWERED_FIELD), true)
                )
            );
            
            TypedQuery<Long> typedQuery = entityManager.createQuery(query);
            return typedQuery.getSingleResult();
        } catch (Exception e) {
            log.error("Error counting answered questions for game {}: {}", game.getId(), e.getMessage(), e);
            return 0L;
        }
    }

    /**
     * Count total questions in a game
     */
    public long countTotalQuestions(Game game) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> query = cb.createQuery(Long.class);
            Root<GameQuestion> root = query.from(GameQuestion.class);
            
            query.select(cb.count(root));
            query.where(cb.equal(root.get(GAME_FIELD), game));
            
            TypedQuery<Long> typedQuery = entityManager.createQuery(query);
            return typedQuery.getSingleResult();
        } catch (Exception e) {
            log.error("Error counting total questions for game {}: {}", game.getId(), e.getMessage(), e);
            return 0L;
        }
    }

    /**
     * Find answered questions in a game ordered by question order
     */
    public List<GameQuestion> findAnsweredQuestions(Game game) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<GameQuestion> query = querySupplier.get();
            Root<GameQuestion> root = query.from(GameQuestion.class);
            
            query.where(
                cb.and(
                    cb.equal(root.get(GAME_FIELD), game),
                    cb.equal(root.get(ANSWERED_FIELD), true)
                )
            );
            
            query.orderBy(cb.asc(root.get(QUESTION_ORDER_FIELD)));
            
            TypedQuery<GameQuestion> typedQuery = entityManager.createQuery(query);
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding answered questions for game {}: {}", game.getId(), e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find unanswered questions in a game ordered by question order
     */
    public List<GameQuestion> findUnansweredQuestions(Game game) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<GameQuestion> query = querySupplier.get();
            Root<GameQuestion> root = query.from(GameQuestion.class);
            
            query.where(
                cb.and(
                    cb.equal(root.get(GAME_FIELD), game),
                    cb.equal(root.get(ANSWERED_FIELD), false)
                )
            );
            
            query.orderBy(cb.asc(root.get(QUESTION_ORDER_FIELD)));
            
            TypedQuery<GameQuestion> typedQuery = entityManager.createQuery(query);
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding unanswered questions for game {}: {}", game.getId(), e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Count correctly answered questions in a game
     */
    public long countCorrectAnswers(Game game) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> query = cb.createQuery(Long.class);
            Root<GameQuestion> root = query.from(GameQuestion.class);
            
            query.select(cb.count(root));
            query.where(
                cb.and(
                    cb.equal(root.get(GAME_FIELD), game),
                    cb.equal(root.get(ANSWERED_FIELD), true),
                    cb.equal(root.get("isCorrect"), true)
                )
            );
            
            TypedQuery<Long> typedQuery = entityManager.createQuery(query);
            return typedQuery.getSingleResult();
        } catch (Exception e) {
            log.error("Error counting correct answers for game {}: {}", game.getId(), e.getMessage(), e);
            return 0L;
        }
    }

    /**
     * Find the next question order number for a game
     */
    public int getNextQuestionOrder(Game game) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Integer> query = cb.createQuery(Integer.class);
            Root<GameQuestion> root = query.from(GameQuestion.class);
            
            query.select(cb.max(root.get(QUESTION_ORDER_FIELD)));
            query.where(cb.equal(root.get(GAME_FIELD), game));
            
            TypedQuery<Integer> typedQuery = entityManager.createQuery(query);
            Integer maxOrder = typedQuery.getSingleResult();
            
            return maxOrder == null ? 1 : maxOrder + 1;
        } catch (Exception e) {
            log.error("Error getting next question order for game {}: {}", game.getId(), e.getMessage(), e);
            return 1;
        }
    }

    @Override
    protected Class<GameQuestion> getEntityClass() {
        return GameQuestion.class;
    }
}