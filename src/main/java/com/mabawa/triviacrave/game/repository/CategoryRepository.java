package com.mabawa.triviacrave.game.repository;

import com.mabawa.triviacrave.common.repository.AbstractEntityRepo;
import com.mabawa.triviacrave.game.entity.Category;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
public class CategoryRepository extends AbstractEntityRepo<Category, Long> {
    private static final String NAME_FIELD = "name";

    @Autowired
    public CategoryRepository(EntityManager entityManager) {
        super(entityManager, () -> entityManager.getCriteriaBuilder().createQuery(Category.class));
    }

    /**
     * Find category by name (case-insensitive)
     */
    public Optional<Category> findByName(String name) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Category> query = querySupplier.get();
            Root<Category> root = query.from(Category.class);
            
            query.where(cb.equal(cb.lower(root.get(NAME_FIELD)), name.toLowerCase()));
            
            TypedQuery<Category> typedQuery = entityManager.createQuery(query);
            List<Category> results = typedQuery.getResultList();
            
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.error("Error finding category by name {}: {}", name, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Find all categories ordered by name
     */
    public List<Category> findAllOrderedByName() {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Category> query = querySupplier.get();
            Root<Category> root = query.from(Category.class);
            
            query.orderBy(cb.asc(root.get(NAME_FIELD)));
            
            TypedQuery<Category> typedQuery = entityManager.createQuery(query);
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding all categories: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Check if category exists by name
     */
    public boolean existsByName(String name) {
        return findByName(name).isPresent();
    }

    /**
     * Count total number of categories
     */
    public long countAll() {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> query = cb.createQuery(Long.class);
            Root<Category> root = query.from(Category.class);
            
            query.select(cb.count(root));
            
            TypedQuery<Long> typedQuery = entityManager.createQuery(query);
            return typedQuery.getSingleResult();
        } catch (Exception e) {
            log.error("Error counting categories: {}", e.getMessage(), e);
            return 0L;
        }
    }

    /**
     * Find all active categories ordered by name
     */
    public List<Category> findActiveCategories() {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Category> query = querySupplier.get();
            Root<Category> root = query.from(Category.class);
            
            query.where(cb.isTrue(root.get("isActive")));
            query.orderBy(cb.asc(root.get(NAME_FIELD)));
            
            TypedQuery<Category> typedQuery = entityManager.createQuery(query);
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding active categories: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    protected Class<Category> getEntityClass() {
        return Category.class;
    }
}