package com.mabawa.triviacrave.user.repository;

import com.mabawa.triviacrave.common.repository.AbstractEntityRepo;
import com.mabawa.triviacrave.user.entity.PasswordResetToken;
import com.mabawa.triviacrave.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Root;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class PasswordResetTokenRepository extends AbstractEntityRepo<PasswordResetToken, Long> {
    private static final String TOKEN_FIELD = "token";
    private static final String USER_FIELD = "user";
    private static final String USED_FIELD = "used";
    private static final String EXPIRY_DATE_FIELD = "expiryDate";

    @Autowired
    public PasswordResetTokenRepository(EntityManager entityManager) {
        super(entityManager, () -> entityManager.getCriteriaBuilder().createQuery(PasswordResetToken.class));
    }

    public Optional<PasswordResetToken> findByToken(String token) {
        return Optional.ofNullable(findOneByField(TOKEN_FIELD, token));
    }

    public Optional<PasswordResetToken> findValidTokenByUser(User user) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<PasswordResetToken> query = cb.createQuery(PasswordResetToken.class);
        Root<PasswordResetToken> root = query.from(PasswordResetToken.class);

        query.select(root)
                .where(
                        cb.and(
                                cb.equal(root.get(USER_FIELD), user),
                                cb.equal(root.get(USED_FIELD), false),
                                cb.greaterThan(root.get(EXPIRY_DATE_FIELD), LocalDateTime.now())
                        )
                )
                .orderBy(cb.desc(root.get("createdAt")));

        return entityManager.createQuery(query)
                .getResultList()
                .stream()
                .findFirst();
    }

    public void markAllUserTokensAsUsed(User user) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaUpdate<PasswordResetToken> update = cb.createCriteriaUpdate(PasswordResetToken.class);
        Root<PasswordResetToken> root = update.from(PasswordResetToken.class);

        update.set(root.get(USED_FIELD), true)
                .where(
                        cb.and(
                                cb.equal(root.get(USER_FIELD), user),
                                cb.equal(root.get(USED_FIELD), false)
                        )
                );

        entityManager.createQuery(update).executeUpdate();
    }

    public void deleteExpiredTokens() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<PasswordResetToken> query = cb.createQuery(PasswordResetToken.class);
        Root<PasswordResetToken> root = query.from(PasswordResetToken.class);

        query.select(root)
                .where(cb.lessThan(root.get(EXPIRY_DATE_FIELD), LocalDateTime.now()));

        entityManager.createQuery(query)
                .getResultList()
                .forEach(this::delete);
    }

    @Override
    protected Class<PasswordResetToken> getEntityClass() {
        return PasswordResetToken.class;
    }
}