package com.secondlife.secondlife.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackages = "com.secondlife.secondlife.repository",
        repositoryBaseClass = JpaRepositoryConfiguration.ManagedJpaRepository.class)
public class JpaRepositoryConfiguration {
    public interface RefreshOperations {
        void refreshManaged(Object entity);

        void refreshManaged(Object entity, LockModeType lockMode);
    }

    public static class ManagedJpaRepository<T, ID> extends SimpleJpaRepository<T, ID>
            implements RefreshOperations {
        private final EntityManager entityManager;

        public ManagedJpaRepository(JpaEntityInformation<T, ?> information, EntityManager entityManager) {
            super(information, entityManager);
            this.entityManager = entityManager;
        }

        @Override
        @Transactional(propagation = Propagation.MANDATORY)
        public void refreshManaged(Object entity) {
            entityManager.refresh(entity);
        }

        @Override
        @Transactional(propagation = Propagation.MANDATORY)
        public void refreshManaged(Object entity, LockModeType lockMode) {
            entityManager.refresh(entity, lockMode);
        }
    }
}
