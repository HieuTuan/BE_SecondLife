package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.InspectionChecklistTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionChecklistTemplateRepository extends JpaRepository<InspectionChecklistTemplate, UUID> {

    List<InspectionChecklistTemplate> findByCategory_IdAndActiveTrue(UUID categoryId);

    default List<InspectionChecklistTemplate> findByCategoryIdAndActiveTrue(UUID categoryId) {
        return findByCategory_IdAndActiveTrue(categoryId);
    }

    List<InspectionChecklistTemplate> findByActiveTrueOrderByCreatedAtDesc();

    @Query("SELECT t FROM InspectionChecklistTemplate t LEFT JOIN FETCH t.items WHERE t.id = :id")
    Optional<InspectionChecklistTemplate> findByIdWithItems(UUID id);

    @Query("SELECT t FROM InspectionChecklistTemplate t LEFT JOIN FETCH t.items WHERE t.category.id = :categoryId AND t.active = true")
    List<InspectionChecklistTemplate> findByCategoryWithItems(UUID categoryId);
}
