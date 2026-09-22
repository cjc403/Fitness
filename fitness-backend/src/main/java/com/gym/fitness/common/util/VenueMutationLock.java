package com.gym.fitness.common.util;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.module.organization.repository.VenueRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes same-venue booking mutations without blocking PostgreSQL foreign-key KEY SHARE checks. */
@Component @RequiredArgsConstructor
public class VenueMutationLock {
    private final EntityManager entityManager;
    private final VenueRepository venues;

    @Transactional(propagation=Propagation.MANDATORY)
    public void acquire(Long id) {
        boolean found;
        var dialect=entityManager.getEntityManagerFactory().unwrap(SessionFactoryImplementor.class).getJdbcServices().getDialect();
        if(dialect instanceof PostgreSQLDialect) {
            found=!entityManager.createNativeQuery("SELECT id FROM venue WHERE id=:id FOR NO KEY UPDATE")
                    .setParameter("id",id).getResultList().isEmpty();
        } else found=venues.findByIdForUpdate(id).isPresent();
        if(!found)throw new BusinessException(ErrorCode.NOT_FOUND,"门店不存在");
    }
}
