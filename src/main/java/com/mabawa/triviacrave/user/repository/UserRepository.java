package com.mabawa.triviacrave.user.repository;

import com.mabawa.triviacrave.common.repository.AbstractEntityRepo;
import com.mabawa.triviacrave.user.entity.User;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository extends AbstractEntityRepo<User, Long> {
    private static final String EMAIL_FIELD = "email";
    private static final String PHONE_NUMBER_FIELD = "phoneNumber";

    @Autowired
    public UserRepository(EntityManager entityManager) {
        super(entityManager, () -> entityManager.getCriteriaBuilder().createQuery(User.class));
    }

    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(findOneByField(EMAIL_FIELD, email));
    }

    public Optional<User> findByPhoneNumber(String phoneNumber) {
        return Optional.ofNullable(findOneByField(PHONE_NUMBER_FIELD, phoneNumber));
    }

    @Override
    protected Class<User> getEntityClass() {
        return User.class;
    }
}
