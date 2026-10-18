package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.dto.response.SellerReputationData.Event;
import com.secondlife.secondlife.entity.SellerReputation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SellerReputationRepository extends JpaRepository<SellerReputation, UUID> {
    interface StateRow {
        long getGood();
        long getBad();
        long getIncidents();
        long getSerious();
        boolean getRestricted();
        Instant getUpdated();
        BigDecimal getPenalty();
    }

    interface MetricsRow {
        long getCancellations();
        long getReturns();
        long getNfr();
        long getHandovers();
        long getLate();
    }

    interface HistoryRow {
        UUID getId();
        String getEventType();
        UUID getOrderId();
        UUID getReportId();
        long getGoodBefore();
        long getBadBefore();
        long getGoodAfter();
        long getBadAfter();
        BigDecimal getScoreBefore();
        BigDecimal getScoreAfter();
        Long getPriorGood();
        Long getPriorTotal();
        Instant getCreatedAt();
        String getFormulaVersion();
        BigDecimal getPenaltyBefore();
        BigDecimal getPenaltyAfter();
        Long getEventSequence();
    }

    // Scalar snapshots always observe native counter writes, including within the same persistence context.
    @Query(value = """
        SELECT good_transactions AS good,bad_transactions AS bad,confirmed_incidents AS incidents,
            serious_incidents AS serious,selling_restricted AS restricted,updated_at AS updated,
            confirmation_penalty_points AS penalty FROM seller_reputations WHERE seller_id=:sellerId
        """, nativeQuery = true)
    Optional<StateRow> findState(@Param("sellerId") UUID sellerId);

    @Query(value = """
        SELECT good_transactions AS good,bad_transactions AS bad,confirmed_incidents AS incidents,
            serious_incidents AS serious,selling_restricted AS restricted,updated_at AS updated,
            confirmation_penalty_points AS penalty FROM seller_reputations WHERE seller_id=:sellerId FOR UPDATE
        """, nativeQuery = true)
    Optional<StateRow> findStateForUpdate(@Param("sellerId") UUID sellerId);

    @Modifying
    @Query(value = "INSERT INTO seller_reputations(seller_id) VALUES (:sellerId) ON CONFLICT (seller_id) DO NOTHING", nativeQuery = true)
    void initializeSellerState(@Param("sellerId") UUID sellerId);

    @Query(value = """
        SELECT COUNT(*) FILTER(WHERE EXISTS(SELECT 1 FROM reputation_order_faults f WHERE f.order_id=x.id AND f.fault_type='CANCELLATION')) AS cancellations,
            COUNT(*) FILTER(WHERE EXISTS(SELECT 1 FROM reputation_order_faults f WHERE f.order_id=x.id AND f.fault_type='RETURN')) AS returns,
            COUNT(*) FILTER(WHERE EXISTS(SELECT 1 FROM reputation_order_faults f WHERE f.order_id=x.id AND f.fault_type IN ('CANCELLATION','RETURN'))) AS nfr,
            COUNT(*) FILTER(WHERE x.handover_deadline IS NOT NULL AND x.carrier_picked_up_at IS NOT NULL) AS handovers,
            COUNT(*) FILTER(WHERE EXISTS(SELECT 1 FROM reputation_order_faults f WHERE f.order_id=x.id AND f.fault_type='LATE_HANDOVER')) AS late
        FROM reputation_order_outcomes o JOIN orders x ON x.id=o.order_id WHERE o.seller_id=:sellerId
        """, nativeQuery = true)
    MetricsRow findMetrics(@Param("sellerId") UUID sellerId);

    @Query(value = "SELECT COUNT(*) FROM seller_reputation_events WHERE seller_id=:sellerId", nativeQuery = true)
    long countHistory(@Param("sellerId") UUID sellerId);

    @Query(value = """
        SELECT id,event_type AS "eventType",order_id AS "orderId",report_id AS "reportId",
            good_before AS "goodBefore",bad_before AS "badBefore",good_after AS "goodAfter",bad_after AS "badAfter",
            score_before AS "scoreBefore",score_after AS "scoreAfter",prior_good AS "priorGood",prior_total AS "priorTotal",
            created_at AS "createdAt",formula_version AS "formulaVersion",penalty_before AS "penaltyBefore",
            penalty_after AS "penaltyAfter",event_sequence AS "eventSequence"
        FROM seller_reputation_events WHERE seller_id=:sellerId
        ORDER BY event_sequence DESC NULLS LAST,created_at DESC,id DESC LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<HistoryRow> findHistory(@Param("sellerId") UUID sellerId, @Param("limit") int limit, @Param("offset") long offset);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM seller_reputations WHERE seller_id=:sellerId AND selling_restricted)", nativeQuery = true)
    boolean isSellingRestricted(@Param("sellerId") UUID sellerId);

    @Query(value = "SELECT serious FROM reputation_incidents WHERE incident_key=:incidentKey", nativeQuery = true)
    List<Boolean> findIncidentSeverities(@Param("incidentKey") String incidentKey);

    @Modifying
    @Query(value = "INSERT INTO reputation_incidents(incident_key,seller_id,source_report_id,serious,created_at) VALUES (:incidentKey,:sellerId,:reportId,:serious,CURRENT_TIMESTAMP)", nativeQuery = true)
    void createIncident(@Param("incidentKey") String incidentKey, @Param("sellerId") UUID sellerId,
                        @Param("reportId") UUID reportId, @Param("serious") boolean serious);

    @Modifying
    @Query(value = "UPDATE reputation_incidents SET serious=TRUE,source_report_id=:reportId WHERE incident_key=:incidentKey", nativeQuery = true)
    void escalateIncident(@Param("incidentKey") String incidentKey, @Param("reportId") UUID reportId);

    @Modifying
    @Query(value = """
        UPDATE seller_reputations SET confirmed_incidents=confirmed_incidents+:confirmedIncrement,serious_incidents=serious_incidents+:seriousIncrement,
            selling_restricted=CASE WHEN :restrictSelling THEN TRUE ELSE selling_restricted END,updated_at=CURRENT_TIMESTAMP WHERE seller_id=:sellerId
        """, nativeQuery = true)
    void incrementIncidentCounts(@Param("sellerId") UUID sellerId, @Param("confirmedIncrement") int confirmedIncrement,
                                 @Param("seriousIncrement") int seriousIncrement, @Param("restrictSelling") boolean restrictSelling);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM reputation_order_faults WHERE order_id=:orderId)", nativeQuery = true)
    boolean hasOrderFault(@Param("orderId") UUID orderId);

    @Modifying
    @Query(value = """
        INSERT INTO reputation_order_faults(order_id,seller_id,fault_type,source_report_id,actor_id,reason)
        VALUES (:orderId,:sellerId,:type,:reportId,:actorId,:reason) ON CONFLICT(order_id,fault_type) DO NOTHING
        """, nativeQuery = true)
    int addOrderFaultIfAbsent(@Param("orderId") UUID orderId, @Param("sellerId") UUID sellerId, @Param("type") String type,
                             @Param("reportId") UUID reportId, @Param("actorId") UUID actorId, @Param("reason") String reason);

    @Query(value = "SELECT outcome FROM reputation_order_outcomes WHERE order_id=:orderId", nativeQuery = true)
    String findOrderOutcome(@Param("orderId") UUID orderId);

    @Modifying
    @Query(value = "DELETE FROM reputation_order_outcomes WHERE order_id=:orderId", nativeQuery = true)
    void deleteOrderOutcome(@Param("orderId") UUID orderId);

    @Modifying
    @Query(value = """
        INSERT INTO reputation_order_outcomes(order_id,seller_id,outcome,source_report_id,updated_at)
        VALUES (:orderId,:sellerId,:outcome,(SELECT source_report_id FROM reputation_order_faults WHERE order_id=:orderId ORDER BY created_at LIMIT 1),CURRENT_TIMESTAMP)
        ON CONFLICT(order_id) DO UPDATE SET outcome=EXCLUDED.outcome,source_report_id=EXCLUDED.source_report_id,updated_at=EXCLUDED.updated_at
        """, nativeQuery = true)
    void saveOrderOutcome(@Param("orderId") UUID orderId, @Param("sellerId") UUID sellerId, @Param("outcome") String outcome);

    @Modifying
    @Query(value = "UPDATE seller_reputations SET good_transactions=good_transactions+:goodDelta,bad_transactions=bad_transactions+:badDelta,updated_at=CURRENT_TIMESTAMP WHERE seller_id=:sellerId", nativeQuery = true)
    void adjustTransactionCounts(@Param("sellerId") UUID sellerId, @Param("goodDelta") int goodDelta, @Param("badDelta") int badDelta);

    @Modifying
    @Query(value = """
        INSERT INTO order_confirmation_penalties(order_id,milestone,seller_id,points)
        VALUES (:orderId,:milestone,:sellerId,:points) ON CONFLICT(order_id,milestone) DO NOTHING
        """, nativeQuery = true)
    int addConfirmationPenaltyIfAbsent(@Param("orderId") UUID orderId, @Param("milestone") String milestone,
                                       @Param("sellerId") UUID sellerId, @Param("points") BigDecimal points);

    @Modifying
    @Query(value = "UPDATE seller_reputations SET confirmation_penalty_points=confirmation_penalty_points+:points,updated_at=CURRENT_TIMESTAMP WHERE seller_id=:sellerId", nativeQuery = true)
    void increaseConfirmationPenalty(@Param("sellerId") UUID sellerId, @Param("points") BigDecimal points);

    @Modifying
    @Query(value = "UPDATE seller_reputations SET selling_restricted=FALSE,updated_at=CURRENT_TIMESTAMP WHERE seller_id=:sellerId", nativeQuery = true)
    void liftRestriction(@Param("sellerId") UUID sellerId);

    @Modifying
    @Query(value = """
        INSERT INTO seller_reputation_events(id,seller_id,event_type,order_id,report_id,actor_id,reason,
            good_before,bad_before,good_after,bad_after,score_before,score_after,formula_version,penalty_before,penalty_after,created_at)
        VALUES (:#{#event.id()},:#{#event.sellerId()},:#{#event.type()},:#{#event.orderId()},:#{#event.reportId()},:#{#event.actorId()},:#{#event.reason()},
            :#{#event.before().good()},:#{#event.before().bad()},:#{#event.after().good()},:#{#event.after().bad()},:#{#event.scoreBefore()},:#{#event.scoreAfter()},
            :#{#event.formulaVersion()},:#{#event.before().penalty()},:#{#event.after().penalty()},CURRENT_TIMESTAMP)
        """, nativeQuery = true)
    void appendEvent(@Param("event") Event event);
}
