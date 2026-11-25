package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.ReportEvidenceUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReportEvidenceRepository extends JpaRepository<ReportEvidenceUpload, UUID> {
    record Upload(UUID ownerId, UUID reportId, String storageId, String contentType) { }

    @Modifying
    @Query(value = """
            INSERT INTO report_evidence_uploads(id,owner_id,storage_id,content_type,size_bytes)
            VALUES (:id,:ownerId,:storageId,:contentType,:sizeBytes)
            """, nativeQuery = true)
    void saveUpload(@Param("id") UUID id, @Param("ownerId") UUID ownerId, @Param("storageId") String storageId,
            @Param("contentType") String contentType, @Param("sizeBytes") long sizeBytes);

    @Query("select u.ownerId from ReportEvidenceUpload u where u.id=:evidenceId")
    Optional<UUID> findOwnerId(@Param("evidenceId") UUID evidenceId);

    @Modifying
    @Query(value = "UPDATE report_evidence_uploads SET report_id=:reportId WHERE id=:evidenceId AND owner_id=:ownerId AND report_id IS NULL", nativeQuery = true)
    int attachToReport(@Param("evidenceId") UUID evidenceId, @Param("ownerId") UUID ownerId, @Param("reportId") UUID reportId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM report_evidence_uploads u
                JOIN user_reports r ON r.id = u.report_id AND r.reporter_id = u.owner_id
                WHERE u.id = :evidenceId AND r.seller_id = :sellerId AND r.order_id IS NOT NULL
                  AND EXISTS (
                      SELECT 1 FROM jsonb_array_elements_text(r.evidence_urls) AS entry(url)
                      WHERE LOWER(entry.url) = '/api/v1/reports/evidence/' || CAST(u.id AS TEXT)
                  )
            )
            """, nativeQuery = true)
    boolean isInitialBuyerEvidenceForSeller(@Param("evidenceId") UUID evidenceId, @Param("sellerId") UUID sellerId);

    Optional<Upload> findUploadById(UUID evidenceId);
}
