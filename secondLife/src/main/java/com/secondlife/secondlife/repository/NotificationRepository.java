package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdAndIsDeletedFalse(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndIsReadFalseAndIsDeletedFalse(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndTypeAndIsDeletedFalse(UUID userId, String type, Pageable pageable);

    Page<Notification> findByUserIdAndTypeAndIsReadFalseAndIsDeletedFalse(UUID userId, String type, Pageable pageable);

    long countByUserIdAndIsReadFalseAndIsDeletedFalse(UUID userId);

    Optional<Notification> findByIdAndUserIdAndIsDeletedFalse(UUID id, UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.user.id = :userId AND n.isRead = false AND n.isDeleted = false")
    int markAllAsRead(@Param("userId") UUID userId, @Param("readAt") Instant readAt);

    @Modifying
    @Query("UPDATE Notification n SET n.isDeleted = true WHERE n.user.id = :userId AND n.isRead = true AND n.isDeleted = false")
    int softDeleteAllReadByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isDeleted = true WHERE n.user.id = :userId AND n.isDeleted = false")
    int softDeleteAllByUserId(@Param("userId") UUID userId);
}
