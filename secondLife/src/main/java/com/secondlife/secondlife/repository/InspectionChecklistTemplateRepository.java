package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.InspectionChecklistTemplate;
import com.secondlife.secondlife.enums.InspectionChecklistStatus;
import com.secondlife.secondlife.enums.InspectionComplexityLevel;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionChecklistTemplateRepository extends JpaRepository<InspectionChecklistTemplate, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from InspectionChecklistTemplate t where t.id = :id")
    Optional<InspectionChecklistTemplate> findByIdForUpdate(@Param("id") UUID id);

    @Query("select t.catalogItem.id from InspectionChecklistTemplate t where t.id = :id")
    Optional<UUID> findCatalogItemId(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"category", "catalogItem", "items", "createdBy", "reviewedBy"})
    Optional<InspectionChecklistTemplate> findByCatalogItem_IdAndApprovalStatusAndActiveTrue(
            UUID itemId, InspectionChecklistStatus status);

    @Query("""
            select t.id from InspectionChecklistTemplate t
            where (:status is null or t.approvalStatus = :status)
              and (:authorId is null or t.createdBy.id = :authorId)
            """)
    Page<UUID> findProposalIds(@Param("authorId") UUID authorId,
                               @Param("status") InspectionChecklistStatus status, Pageable pageable);

    @Query("""
            select t.id from InspectionChecklistTemplate t
            left join t.category category
            left join t.catalogItem item
            left join t.createdBy author
            left join author.profile profile
            where (:authorId is null or author.id = :authorId)
              and (:status is null or t.approvalStatus = :status)
              and (:categoryId is null or category.id = :categoryId)
              and (:itemId is null or item.id = :itemId)
              and (:complexityLevel is null or t.complexityLevel = :complexityLevel)
              and (:inspectionCenterId is null or author.id = :inspectionCenterId)
              and (:active is null or t.active = :active)
              and (:pattern is null or cast(function('translate',
                  cast(function('regexp_replace',
                      lower(concat(coalesce(item.name, ''), ' ', coalesce(category.name, ''), ' ',
                          coalesce(t.title, ''), ' ', coalesce(t.description, ''), ' ',
                          coalesce(profile.fullName, ''), ' ', coalesce(author.email, ''))),
                      '[\u0300-\u036f]', '', 'g') as string),
                  :accentedCharacters, :replacementCharacters) as string) like :pattern escape '!')
            """)
    Page<UUID> searchProposalIds(
            @Param("authorId") UUID authorId,
            @Param("status") InspectionChecklistStatus status,
            @Param("categoryId") UUID categoryId,
            @Param("itemId") UUID itemId,
            @Param("complexityLevel") InspectionComplexityLevel complexityLevel,
            @Param("inspectionCenterId") UUID inspectionCenterId,
            @Param("active") Boolean active,
            @Param("pattern") String pattern,
            @Param("accentedCharacters") String accentedCharacters,
            @Param("replacementCharacters") String replacementCharacters,
            Pageable pageable);

    @EntityGraph(attributePaths = {"category", "catalogItem", "items", "createdBy.profile", "reviewedBy.profile"})
    @Query("select distinct t from InspectionChecklistTemplate t where t.id in :ids")
    List<InspectionChecklistTemplate> findDetailsByIds(@Param("ids") List<UUID> ids);

    @EntityGraph(attributePaths = {"category", "catalogItem", "items", "createdBy", "reviewedBy"})
    @Query("""
            select distinct t from InspectionChecklistTemplate t
            where (t.approvalStatus = 'APPROVED' and t.active = true)
               or t.createdBy.id = :authorId
            order by t.createdAt desc
            """)
    List<InspectionChecklistTemplate> findVisibleToCenter(@Param("authorId") UUID authorId);

    List<InspectionChecklistTemplate> findByCategory_IdAndActiveTrue(UUID categoryId);

    @EntityGraph(attributePaths = {"category", "catalogItem", "items", "createdBy", "reviewedBy"})
    List<InspectionChecklistTemplate> findByActiveTrueOrderByCreatedAtDesc();

    @Query("SELECT t FROM InspectionChecklistTemplate t LEFT JOIN FETCH t.items WHERE t.id = :id")
    Optional<InspectionChecklistTemplate> findByIdWithItems(UUID id);

    @Query("SELECT t FROM InspectionChecklistTemplate t LEFT JOIN FETCH t.items WHERE t.category.id = :categoryId AND t.active = true")
    List<InspectionChecklistTemplate> findByCategoryWithItems(UUID categoryId);
}
