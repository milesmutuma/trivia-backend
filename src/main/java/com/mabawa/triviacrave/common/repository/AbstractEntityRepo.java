package com.mabawa.triviacrave.common.repository;

import com.mabawa.triviacrave.common.repository.error.DBException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Supplier;

@Slf4j
public abstract class AbstractEntityRepo<T, ID> {
    protected final EntityManager entityManager;
    protected final Supplier<CriteriaQuery<T>> querySupplier;
    protected final SingularAttribute<? super T, ID> idAttribute;

    public AbstractEntityRepo(EntityManager entityManager, Supplier<CriteriaQuery<T>> querySupplier) {
        this.entityManager = entityManager;
        this.querySupplier = querySupplier;
        this.idAttribute = determineIdAttribute();
    }

    protected abstract Class<T> getEntityClass();

    @SuppressWarnings("unchecked")
    private SingularAttribute<? super T, ID> determineIdAttribute() {
        EntityType<T> entityType = entityManager.getMetamodel().entity(getEntityClass());
        return (SingularAttribute<? super T, ID>) entityType.getId(entityType.getIdType().getJavaType());
    }

    @SuppressWarnings("unchecked")
    private ID getEntityId(T entity) {
        return (ID) entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
    }

    private boolean entityExists(T entity) {
        ID id = getEntityId(entity);
        if (id == null) return false;
        return entityManager.find(getEntityClass(), id) != null;
    }

    @Transactional
    public T save(T entity) {
        try {
            if (entityManager.contains(entity) || entityExists(entity)) {
                return entityManager.merge(entity);
            } else {
                entityManager.persist(entity);
                return entity;
            }
        } catch (Exception e) {
            log.error("Error saving entity: {}", e.getMessage(), e);
            throw new DBException("Failed to save entity. Error -> %s".formatted(e.getMessage()));
        }
    }

    @Transactional
    public void delete(T entity) {
        try {
            T managedEntity = entityManager.contains(entity) ? entity : entityManager.find(getEntityClass(), getEntityId(entity));
            if (managedEntity != null) {
                entityManager.remove(managedEntity);
            } else {
                throw new DBException("Entity does not exist.");
            }
        } catch (Exception e) {
            log.error("Error deleting entity: {}", e.getMessage(), e);
            throw new DBException("Failed to delete entity. Error -> %s".formatted(e.getMessage()));
        }
    }

    public T findOne(ID id) {
        try {
            return entityManager.find(getEntityClass(), id);
        } catch (Exception e) {
            log.error("Error finding entity with ID {}: {}", id, e.getMessage(), e);
            throw new DBException("Failed to find entity. Error -> %s".formatted(e.getMessage()));
        }
    }

    public T findOneByField(String fieldName, String value) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = querySupplier.get();
        Root<T> root = query.from(getEntityClass());
        Predicate predicate = buildEqualPredicate(cb, root, fieldName, value);
        query.where(predicate);
        TypedQuery<T> typedQuery = entityManager.createQuery(query);
        try {
            return typedQuery.getSingleResult();
        } catch (NoResultException e) {
            return null;
        } catch (NonUniqueResultException e) {
            throw new DBException("Multiple entities found for the given criteria.");
        } catch (Exception e) {
            log.error("Error finding entity by field {}: {}", fieldName, e.getMessage(), e);
            throw new DBException("Failed to find entity by field.");
        }
    }

    public Optional<T> filterSingle(Map<String, String> fieldValueMap) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = querySupplier.get();
        Root<T> root = query.from(getEntityClass());
        List<Predicate> predicates = new ArrayList<>();
        for (Map.Entry<String, String> entry : fieldValueMap.entrySet()) {
            predicates.add(buildEqualPredicate(cb, root, entry.getKey(), entry.getValue()));
        }
        if (!predicates.isEmpty()) query.where(cb.and(predicates.toArray(new Predicate[0])));
        TypedQuery<T> typedQuery = entityManager.createQuery(query);
        try {
            return Optional.ofNullable(typedQuery.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        } catch (NonUniqueResultException e) {
            throw new DBException("Multiple entities found for the given criteria.");
        } catch (Exception e) {
            throw new DBException("Failed to execute query.");
        }
    }

    private static <T> Predicate buildEqualPredicate(CriteriaBuilder cb, Root<T> root, String field, String value) {
        Path<?> path = getPath(root, field);
        Object parsedValue = value;
        return cb.equal(path, parsedValue);
    }

    private static <T> Path<?> getPath(Root<T> root, String field) {
        String[] parts = field.split("\\.");
        Path<?> path = root;
        for (String part : parts) {
            path = path.get(part);
        }
        return path;
    }

    public List<T> findAll() {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<T> query = querySupplier.get();
            Root<T> root = query.from(getEntityClass());
            query.select(root);
            TypedQuery<T> typedQuery = entityManager.createQuery(query);
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error finding all entities: {}", e.getMessage(), e);
            throw new DBException("Failed to find all entities. Error -> %s".formatted(e.getMessage()));
        }
    }

    public long countAll() {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> query = cb.createQuery(Long.class);
            Root<T> root = query.from(getEntityClass());
            query.select(cb.count(root));
            TypedQuery<Long> typedQuery = entityManager.createQuery(query);
            return typedQuery.getSingleResult();
        } catch (Exception e) {
            log.error("Error counting all entities: {}", e.getMessage(), e);
            throw new DBException("Failed to count all entities. Error -> %s".formatted(e.getMessage()));
        }
    }
}
