package com.secondlife.secondlife.repository;
import com.secondlife.secondlife.entity.VideoUploadSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
public interface VideoUploadSessionRepository extends JpaRepository<VideoUploadSession,UUID> {
    Optional<VideoUploadSession> findByOwnerIdAndRequestId(UUID ownerId,UUID requestId);
    long countByOwnerIdAndCreatedAtAfter(UUID ownerId, Instant after);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from VideoUploadSession s where s.id=:id")
    Optional<VideoUploadSession> findByIdForUpdate(@Param("id") UUID id);
}
