package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Post> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "images")
    java.util.Optional<Post> findByIdAndStatus(UUID id, String status);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "images")
    @org.springframework.data.jpa.repository.Query("select p from Post p where p.id in :ids")
    java.util.List<Post> findAllWithImagesByIdIn(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);
    long countByUser_IdAndStatusIn(UUID userId, java.util.Collection<String> statuses);
    long countByUser_IdAndPublishedAtAfter(UUID userId, java.time.Instant since);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Post p where p.id = :id")
    java.util.Optional<Post> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    java.util.List<Post> findByUser_IdAndIdNotAndStatusIn(UUID userId, UUID id, java.util.Collection<String> statuses);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"images", "user"})
    java.util.List<Post> findByIdNotAndStatusIn(UUID id, java.util.Collection<String> statuses);
    org.springframework.data.domain.Page<Post> findByStatus(String status, org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Query("select p.user.id from Post p where p.id = :id")
    java.util.Optional<UUID> findOwnerId(@org.springframework.data.repository.query.Param("id") UUID id);
}
