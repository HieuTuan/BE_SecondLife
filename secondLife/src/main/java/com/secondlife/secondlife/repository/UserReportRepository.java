package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.dto.request.ReportRequests.Create;
import com.secondlife.secondlife.dto.request.ReportRequests.Decision;
import com.secondlife.secondlife.dto.request.ReportRequests.ReasonCode;
import com.secondlife.secondlife.dto.request.ReportRequests.ResponsibleParty;
import com.secondlife.secondlife.config.JpaRepositoryConfiguration.RefreshOperations;
import com.secondlife.secondlife.entity.UserReport;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserReportRepository extends JpaRepository<UserReport, UUID>, RefreshOperations {
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtextextended('report-create:' || CAST(:reporterId AS TEXT),0))", nativeQuery = true)
    int lockReporter(@Param("reporterId") UUID reporterId);

    @Query(value = "SELECT * FROM user_reports WHERE reporter_id=:reporterId AND request_id=:requestId", nativeQuery = true)
    List<UserReport> findByRequest(@Param("reporterId") UUID reporterId, @Param("requestId") UUID requestId);

    default Optional<UserReport> findById(UUID id, boolean lock) {
        var report = findById(id);
        report.ifPresent(value -> {
            // Native assignments/resolutions and earlier reads can leave this entity cached.
            if (lock) refreshManaged(value, LockModeType.PESSIMISTIC_WRITE);
            else refreshManaged(value);
        });
        return report;
    }

    @Query(value = """
            SELECT EXISTS(SELECT 1 FROM user_reports WHERE reporter_id=:reporterId AND post_id=:postId
                AND order_id IS NOT DISTINCT FROM CAST(:orderId AS UUID) AND reason_code=:#{#reason.name()}
                AND status IN ('OPEN','IN_REVIEW'))
            """, nativeQuery = true)
    boolean hasPending(@Param("reporterId") UUID reporterId, @Param("postId") UUID postId,
            @Param("orderId") UUID orderId, @Param("reason") ReasonCode reason);

    @Modifying
    @Query(value = """
            INSERT INTO user_reports(id,reporter_id,request_id,seller_id,post_id,order_id,reason_code,description,evidence_urls,related_post_id,status,created_at,updated_at)
            VALUES (:id,:reporterId,:#{#request.requestId()},:sellerId,:postId,:#{#request.orderId()},
                :#{#request.reasonCode().name()},:#{#request.description().trim()},CAST(:evidenceJson AS JSONB),
                :#{#request.relatedPostId()},'OPEN',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    void insert(@Param("id") UUID id, @Param("reporterId") UUID reporterId, @Param("sellerId") UUID sellerId,
            @Param("postId") UUID postId, @Param("request") Create request, @Param("evidenceJson") String evidenceJson);

    @Modifying
    @Query(value = "UPDATE user_reports SET status='IN_REVIEW',reviewer_id=:reviewerId,updated_at=CURRENT_TIMESTAMP WHERE id=:id", nativeQuery = true)
    void assign(@Param("id") UUID id, @Param("reviewerId") UUID reviewerId);

    @Modifying
    @Query(value = """
            UPDATE user_reports SET status=:#{#decision.name()},responsible_party=:#{#party.name()},
                decision_reason=:reason,resolved_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=:id
            """, nativeQuery = true)
    void resolve(@Param("id") UUID id, @Param("decision") Decision decision,
            @Param("party") ResponsibleParty party, @Param("reason") String reason);

    @Query(value = """
            SELECT * FROM user_reports WHERE reporter_id=:reporterId
                AND (CAST(:type AS TEXT) IS NULL OR (:type='LISTING' AND order_id IS NULL) OR (:type='ORDER' AND order_id IS NOT NULL))
            ORDER BY created_at DESC,id DESC
            """, countQuery = """
            SELECT COUNT(*) FROM user_reports WHERE reporter_id=:reporterId
                AND (CAST(:type AS TEXT) IS NULL OR (:type='LISTING' AND order_id IS NULL) OR (:type='ORDER' AND order_id IS NOT NULL))
            """, nativeQuery = true)
    Page<UserReport> mine(@Param("reporterId") UUID reporterId, @Param("type") String type, Pageable page);

    @Query(value = """
            SELECT * FROM user_reports WHERE (CAST(:status AS TEXT) IS NULL OR status=:status)
                AND (CAST(:type AS TEXT) IS NULL OR (:type='LISTING' AND order_id IS NULL) OR (:type='ORDER' AND order_id IS NOT NULL))
            ORDER BY created_at DESC,id DESC
            """, countQuery = """
            SELECT COUNT(*) FROM user_reports WHERE (CAST(:status AS TEXT) IS NULL OR status=:status)
                AND (CAST(:type AS TEXT) IS NULL OR (:type='LISTING' AND order_id IS NULL) OR (:type='ORDER' AND order_id IS NOT NULL))
            """, nativeQuery = true)
    Page<UserReport> queue(@Param("status") String status, @Param("type") String type, Pageable page);
}
