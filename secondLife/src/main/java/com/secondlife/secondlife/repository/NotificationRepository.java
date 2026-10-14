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

    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId AND n.isDeleted = false")
    Page<Notification> findByUserIdAndIsDeletedFalse(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId AND n.isRead = false AND n.isDeleted = false")
    Page<Notification> findByUserIdAndIsReadFalseAndIsDeletedFalse(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId AND n.type = :type AND n.isDeleted = false")
    Page<Notification> findByUserIdAndTypeAndIsDeletedFalse(@Param("userId") UUID userId, @Param("type") String type, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId AND n.type = :type AND n.isRead = false AND n.isDeleted = false")
    Page<Notification> findByUserIdAndTypeAndIsReadFalseAndIsDeletedFalse(@Param("userId") UUID userId, @Param("type") String type, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId AND n.isRead = false AND n.isDeleted = false")
    long countByUserIdAndIsReadFalseAndIsDeletedFalse(@Param("userId") UUID userId);

    @Query("SELECT n FROM Notification n WHERE n.id = :id AND n.user.id = :userId AND n.isDeleted = false")
    Optional<Notification> findByIdAndUserIdAndIsDeletedFalse(@Param("id") UUID id, @Param("userId") UUID userId);

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
