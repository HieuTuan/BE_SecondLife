package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.config.JpaRepositoryConfiguration.RefreshOperations;
import com.secondlife.secondlife.entity.Post;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID>, JpaSpecificationExecutor<Post>, RefreshOperations {
    @EntityGraph(attributePaths = "images")
    Optional<Post> findByIdAndStatus(UUID id, String status);
    @Override
    @EntityGraph(attributePaths = {"images", "user", "user.profile"})
    Optional<Post> findById(UUID id);
    @EntityGraph(attributePaths = "images")
    @Query("select p from Post p where p.id in :ids")
    List<Post> findAllWithImagesByIdIn(@Param("ids") Collection<UUID> ids);
    long countByUser_IdAndStatusIn(UUID userId, Collection<String> statuses);
    long countByUser_IdAndPublishedAtAfter(UUID userId, Instant since);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id = :id")
    Optional<Post> findByIdForUpdate(@Param("id") UUID id);

    List<Post> findByUser_IdAndIdNotAndStatusIn(UUID userId, UUID id, Collection<String> statuses);
    @EntityGraph(attributePaths = {"images", "user"})
    List<Post> findByIdNotAndStatusIn(UUID id, Collection<String> statuses);
    Page<Post> findByStatus(String status, Pageable pageable);
    @Query("select p.user.id from Post p where p.id = :id")
    Optional<UUID> findOwnerId(@Param("id") UUID id);

    @Modifying
    @Query("update Post p set p.template = null where p.template.id = :templateId")
    void clearTemplateReference(@Param("templateId") UUID templateId);

    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(6002256281192384846)", nativeQuery = true)
    void lockForSubmission();

}
