package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.InspectionOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionOrderRepository extends JpaRepository<InspectionOrder, UUID> {
    List<InspectionOrder> findByInspectorId(UUID inspectorId);
    Page<InspectionOrder> findByStatus(String status, Pageable pageable);
    Page<InspectionOrder> findAll(Pageable pageable);
    Optional<InspectionOrder> findByPostId(UUID postId);
}
