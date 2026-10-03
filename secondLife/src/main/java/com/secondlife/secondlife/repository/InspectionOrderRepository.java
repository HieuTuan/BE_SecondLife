package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.InspectionOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionOrderRepository extends JpaRepository<InspectionOrder, UUID> {
    @org.springframework.data.jpa.repository.Query("select o.post.user.id from InspectionOrder o where o.id = :id")
    java.util.Optional<UUID> findOwnerId(@org.springframework.data.repository.query.Param("id") UUID id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from InspectionOrder o where o.id = :id")
    java.util.Optional<InspectionOrder> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
    List<InspectionOrder> findByInspectorId(UUID inspectorId);
    Page<InspectionOrder> findByStatus(String status, Pageable pageable);
    Page<InspectionOrder> findAll(Pageable pageable);
    Optional<InspectionOrder> findByPostId(UUID postId);
}
