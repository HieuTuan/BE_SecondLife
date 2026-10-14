package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.InspectionChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InspectionChecklistItemRepository extends JpaRepository<InspectionChecklistItem, UUID> {

    List<InspectionChecklistItem> findByTemplateIdOrderByOrderIndexAsc(UUID templateId);
}
