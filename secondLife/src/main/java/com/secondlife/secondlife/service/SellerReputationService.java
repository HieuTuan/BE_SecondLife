package com.secondlife.secondlife.service;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.config.ReputationProperties;
import com.secondlife.secondlife.dto.reputation.*;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class SellerReputationService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final com.secondlife.secondlife.repository.OrderRepository orders;
    private final ReputationProperties properties;
    private final ReputationCalculator calculator;
    private final com.secondlife.secondlife.config.OrderConfirmationProperties confirmations;

    public static final String FORMULA_VERSION="TRANSACTION_SUCCESS_RATE_V3";

    private record State(long good, long bad, long incidents, long serious, boolean restricted, Instant updated, java.math.BigDecimal penalty) { }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ReputationResponse get(UUID sellerId) {
        var state = state(sellerId, false).orElse(new State(0,0,0,0,false,null,java.math.BigDecimal.ZERO));
        var user = users.findByIdWithAuthorities(sellerId).orElseThrow(() -> new NotFoundException("Seller not found"));
        if (!user.hasRole("SELLER") && state.updated() == null) throw new NotFoundException("Seller not found");
        long evaluated = state.good() + state.bad();
        var metrics=jdbc.queryForMap("""
            SELECT COUNT(*) FILTER(WHERE EXISTS(SELECT 1 FROM reputation_order_faults f WHERE f.order_id=x.id AND f.fault_type='CANCELLATION')) AS cancellations,
                COUNT(*) FILTER(WHERE EXISTS(SELECT 1 FROM reputation_order_faults f WHERE f.order_id=x.id AND f.fault_type='RETURN')) AS returns,
                COUNT(*) FILTER(WHERE EXISTS(SELECT 1 FROM reputation_order_faults f WHERE f.order_id=x.id AND f.fault_type IN ('CANCELLATION','RETURN'))) AS nfr,
                COUNT(*) FILTER(WHERE x.handover_deadline IS NOT NULL AND x.carrier_picked_up_at IS NOT NULL) AS handovers,
                COUNT(*) FILTER(WHERE EXISTS(SELECT 1 FROM reputation_order_faults f WHERE f.order_id=x.id AND f.fault_type='LATE_HANDOVER')) AS late
            FROM reputation_order_outcomes o JOIN orders x ON x.id=o.order_id WHERE o.seller_id=?
            """,sellerId);
        long cancellations=((Number)metrics.get("cancellations")).longValue(),returns=((Number)metrics.get("returns")).longValue();
        long nfr=((Number)metrics.get("nfr")).longValue(),handovers=((Number)metrics.get("handovers")).longValue(),late=((Number)metrics.get("late")).longValue();
        return new ReputationResponse(sellerId,calculator.score(state.good(),state.bad(),state.penalty()),state.good(),state.bad(),
                evaluated,properties.minEvaluatedOrders(),evaluated==0 ? "NO_TRANSACTIONS" : evaluated<properties.minEvaluatedOrders()
                ? "INSUFFICIENT_EVIDENCE" : "EVALUATED",state.incidents(),state.serious(),state.restricted(),
                state.restricted()?"SELLING_RESTRICTED":state.serious()>0?"SERIOUS_VIOLATION_HISTORY":
                state.penalty().signum()>0?"ORDER_CONFIRMATION_VIOLATION":state.incidents()>0?"CONFIRMED_VIOLATION_HISTORY":null,state.updated(),FORMULA_VERSION,
                cancellations,returns,nfr,percentage(nfr,evaluated),handovers,late,percentage(late,handovers),
                calculator.score(state.good(),state.bad()),state.penalty());
    }

    @Transactional(readOnly=true)
    public PageResponse<ReputationHistoryResponse> history(UUID sellerId, Pageable page) {
        get(sellerId);
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM seller_reputation_events WHERE seller_id=?",Long.class,sellerId);
        var rows = jdbc.query("SELECT * FROM seller_reputation_events WHERE seller_id=? ORDER BY event_sequence DESC NULLS LAST,created_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs,n) -> new ReputationHistoryResponse(rs.getObject("id",UUID.class),rs.getString("event_type"),
                        rs.getObject("order_id",UUID.class),rs.getObject("report_id",UUID.class),
                        rs.getLong("good_before"),rs.getLong("bad_before"),rs.getLong("good_after"),rs.getLong("bad_after"),
                        rs.getBigDecimal("score_before"),rs.getBigDecimal("score_after"),
                        rs.getObject("prior_good",Long.class),rs.getObject("prior_total",Long.class),instant(rs,"created_at"),rs.getString("formula_version"),
                        rs.getBigDecimal("penalty_before"),rs.getBigDecimal("penalty_after"),rs.getObject("event_sequence",Long.class)),
                sellerId,page.getPageSize(),page.getOffset());
        return PageResponse.from(new PageImpl<>(rows,page,count));
    }

    public void requireSellingAllowed(UUID sellerId) {
        var restricted = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM seller_reputations WHERE seller_id=? AND selling_restricted)",Boolean.class,sellerId);
        if (Boolean.TRUE.equals(restricted)) throw new ForbiddenException("Seller is restricted following a confirmed serious violation");
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void recordCompletedSale(Order requestedOrder) {
        Order order=orders.findByIdForUpdate(requestedOrder.getId()).orElseThrow(() -> new NotFoundException("Order not found"));
        if (order.getStatus()!=OrderStatus.COMPLETED || order.getEscrowStatus()!=EscrowStatus.RELEASED)
            throw new ConflictException("Reputation requires a completed order and released escrow");
        UUID sellerId = order.getSeller().getId();
        State before=lock(sellerId);
        if(evaluate(order)) event(sellerId,"ORDER_COMPLETED",order.getId(),null,null,"Completed sale evaluated once",before,state(sellerId,false).orElseThrow());
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void confirmSellerFault(ReportResponse report, UUID reviewerId, String decisionReason) {
        Order order=report.orderId()==null?null:orders.findByIdForUpdate(report.orderId()).orElseThrow();
        String faultType="PRODUCT_OR_REPORT";
        if(order!=null) {
            if(report.reasonCode()==ReportRequests.ReasonCode.LATE_HANDOVER) {
                if(order.getHandoverDeadline()==null || order.getCarrierPickedUpAt()==null || !order.getCarrierPickedUpAt().isAfter(order.getHandoverDeadline()))
                    throw new ConflictException("Late handover requires carrier pickup after the recorded deadline; unknown historical deadlines cannot be penalized");
                faultType="LATE_HANDOVER";
            } else if(report.reasonCode()==ReportRequests.ReasonCode.SELLER_CANCELLATION) {
                if(order.getStatus()!=OrderStatus.CANCELLED || order.getEscrowStatus()!=EscrowStatus.REFUNDED)
                    throw new ConflictException("Seller cancellation review requires a cancelled, refunded order");
                faultType="CANCELLATION";
            } else if(report.reasonCode()==ReportRequests.ReasonCode.RETURN_NOT_AS_DESCRIBED) {
                if(order.getStatus()!=OrderStatus.CANCELLED || order.getEscrowStatus()!=EscrowStatus.REFUNDED || !isReturn(order))
                    throw new ConflictException("Return fault review requires a physically returned and refunded order");
                faultType="RETURN";
            } else if(order.getStatus()==OrderStatus.CANCELLED && order.getEscrowStatus()==EscrowStatus.REFUNDED) {
                faultType=isReturn(order)?"RETURN":"CANCELLATION";
            }
        } else if(Set.of(ReportRequests.ReasonCode.LATE_HANDOVER,ReportRequests.ReasonCode.SELLER_CANCELLATION,
                ReportRequests.ReasonCode.RETURN_NOT_AS_DESCRIBED).contains(report.reasonCode()))
            throw new BadRequestException("This report reason requires an orderId");
        UUID sellerId=report.sellerId(); State before=lock(sellerId);
        boolean serious=report.reasonCode()==ReportRequests.ReasonCode.FRAUD;
        String key=order!=null?"ORDER:"+order.getId():"POST:"+report.postId()+":"+report.reasonCode();
        var incidents=jdbc.query("SELECT serious FROM reputation_incidents WHERE incident_key=?",(rs,n)->rs.getBoolean(1),key);
        boolean first=incidents.isEmpty(),escalation=!first && serious && !incidents.getFirst();
        if(first) jdbc.update("INSERT INTO reputation_incidents(incident_key,seller_id,source_report_id,serious,created_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP)",key,sellerId,report.id(),serious);
        else if(escalation) jdbc.update("UPDATE reputation_incidents SET serious=TRUE,source_report_id=? WHERE incident_key=?",report.id(),key);
        boolean faultAdded=order!=null && fault(order,faultType,report.id(),reviewerId,decisionReason);
        if(!first && !escalation && !faultAdded) return;
        jdbc.update("""
            UPDATE seller_reputations SET confirmed_incidents=confirmed_incidents+?,serious_incidents=serious_incidents+?,
                selling_restricted=CASE WHEN ? THEN TRUE ELSE selling_restricted END,updated_at=CURRENT_TIMESTAMP WHERE seller_id=?
            """,first?1:0,(first || escalation) && serious?1:0,(first || escalation) && serious,sellerId);
        if(order!=null) evaluate(order);
        event(sellerId,escalation?"SERIOUS_VIOLATION_CONFIRMED":"SELLER_FAULT_CONFIRMED",report.orderId(),report.id(),reviewerId,
                decisionReason,before,state(sellerId,false).orElseThrow());
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void initializePaidOrder(Order order) {
        order.setPaidAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        order.setConfirmationDeadline(order.getPaidAt().plus(java.time.Duration.ofHours(confirmations.lateAfterHours())));
        order.setConfirmationCancelAt(order.getPaidAt().plus(java.time.Duration.ofHours(confirmations.cancelAfterHours())));
        order.setConfirmationLatePenalty(confirmations.latePenaltyPoints());
        order.setConfirmationTimeoutPenalty(confirmations.timeoutPenaltyPoints());
        // Inspection-center shipping is not a seller pickup and must not penalize seller readiness.
        String leg=jdbc.queryForObject("SELECT leg FROM shipping_quotes WHERE id=?",String.class,order.getShippingQuoteId());
        if("SELLER_TO_BUYER".equals(leg)) order.setHandoverDeadline(order.getPaidAt().plus(java.time.Duration.ofHours(properties.handoverDeadlineHours())));
    }
    @Transactional
    public OrderFulfillmentResponse readyForPickup(UUID sellerId,UUID orderId) {
        var order=orders.findByIdForUpdate(orderId).orElseThrow(()->new NotFoundException("Order not found"));
        if(!order.getSeller().getId().equals(sellerId)) throw new ForbiddenException("Only this seller can declare goods ready");
        if(order.getSellerReadyAt()!=null) return fulfillment(order);
        if(order.getConfirmationDeadline()!=null && order.getSellerConfirmedAt()==null)
            throw new ConflictException("Confirm the order before declaring goods ready");
        if((order.getStatus()!=OrderStatus.PROCESSING && order.getStatus()!=OrderStatus.CONFIRMED) || order.getEscrowStatus()!=EscrowStatus.HELD || order.getPaidAt()==null
                || order.getHandoverDeadline()==null || order.getCarrierPickedUpAt()!=null)
            throw new ConflictException("Readiness requires a paid seller-pickup order before carrier pickup");
        order.setSellerReadyAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS)); orders.save(order); return fulfillment(order);
    }
    @Transactional(readOnly=true)
    public OrderFulfillmentResponse fulfillment(UUID actorId,boolean reviewer,UUID orderId) {
        var order=orders.findById(orderId).orElseThrow(()->new NotFoundException("Order not found"));
        if(!reviewer && !order.getBuyer().getId().equals(actorId) && !order.getSeller().getId().equals(actorId))
            throw new ForbiddenException("This order belongs to another user");
        return fulfillment(order);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void recordSellerCancellation(Order order,UUID actorId,String reason) {
        State before=lock(order.getSeller().getId());
        boolean added=fault(order,"CANCELLATION",null,actorId,reason); boolean changed=evaluate(order);
        if(added || changed) event(order.getSeller().getId(),"SELLER_CANCELLED_ORDER",order.getId(),null,actorId,reason,before,state(order.getSeller().getId(),false).orElseThrow());
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void recordClosedOrder(Order order,UUID actorId,String reason,boolean sellerReturnFault) {
        State before=lock(order.getSeller().getId()); boolean added=false;
        if(sellerReturnFault) added=fault(order,"RETURN",null,actorId,reason);
        if(evaluate(order) || added) event(order.getSeller().getId(),"CLOSED_ORDER_EVALUATED",order.getId(),null,actorId,reason,before,state(order.getSeller().getId(),false).orElseThrow());
    }
    private OrderFulfillmentResponse fulfillment(Order order) {
        return new OrderFulfillmentResponse(order.getId(),order.getPaidAt(),order.getHandoverDeadline(),order.getSellerReadyAt(),order.getCarrierPickedUpAt(),
                order.getHandoverDeadline()!=null && order.getCarrierPickedUpAt()!=null && order.getCarrierPickedUpAt().isAfter(order.getHandoverDeadline()),
                order.getCancellationReasonCode(),order.getCancelledBy(),order.getReturnResponsibleParty(),
                order.getSellerConfirmedAt(),order.getConfirmationDeadline(),order.getConfirmationCancelAt());
    }
    private boolean isReturn(Order order) {
        return order.getReturnResponsibleParty()!=null || Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM shipments WHERE order_id=? AND leg='BUYER_TO_SELLER')",Boolean.class,order.getId()));
    }
    private boolean hasFault(UUID orderId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM reputation_order_faults WHERE order_id=?)",Boolean.class,orderId));
    }
    private boolean fault(Order order,String type,UUID reportId,UUID actorId,String reason) {
        return jdbc.update("""
            INSERT INTO reputation_order_faults(order_id,seller_id,fault_type,source_report_id,actor_id,reason)
            VALUES (?,?,?,?,?,?) ON CONFLICT(order_id,fault_type) DO NOTHING
            """,order.getId(),order.getSeller().getId(),type,reportId,actorId,reason)==1;
    }
    private boolean evaluate(Order order) {
        boolean bad=hasFault(order.getId()); String next=null;
        if(order.getStatus()==OrderStatus.COMPLETED && order.getEscrowStatus()==EscrowStatus.RELEASED) next=bad?"BAD":"GOOD";
        else if(order.getStatus()==OrderStatus.CANCELLED && order.getEscrowStatus()==EscrowStatus.REFUNDED && bad) next="BAD";
        var previous=jdbc.query("SELECT outcome FROM reputation_order_outcomes WHERE order_id=?",(rs,n)->rs.getString(1),order.getId());
        String old=previous.isEmpty()?null:previous.getFirst();
        if(Objects.equals(old,next)) return false;
        if(next==null) {
            // No pending order contributes to N. Closed non-seller cancellations are excluded as well.
            jdbc.update("DELETE FROM reputation_order_outcomes WHERE order_id=?",order.getId());
        } else {
            jdbc.update("""
                INSERT INTO reputation_order_outcomes(order_id,seller_id,outcome,source_report_id,updated_at)
                VALUES (?,?,?,(SELECT source_report_id FROM reputation_order_faults WHERE order_id=? ORDER BY created_at LIMIT 1),CURRENT_TIMESTAMP)
                ON CONFLICT(order_id) DO UPDATE SET outcome=EXCLUDED.outcome,source_report_id=EXCLUDED.source_report_id,updated_at=EXCLUDED.updated_at
                """,order.getId(),order.getSeller().getId(),next,order.getId());
        }
        jdbc.update("UPDATE seller_reputations SET good_transactions=good_transactions+?,bad_transactions=bad_transactions+?,updated_at=CURRENT_TIMESTAMP WHERE seller_id=?",
                ("GOOD".equals(next)?1:0)-("GOOD".equals(old)?1:0),("BAD".equals(next)?1:0)-("BAD".equals(old)?1:0),order.getSeller().getId());
        return true;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public boolean recordConfirmationPenalty(Order order,String milestone,java.math.BigDecimal points) {
        if(!Set.of("LATE","TIMEOUT").contains(milestone) || points==null || points.signum()<=0)
            throw new IllegalArgumentException("Invalid confirmation penalty");
        UUID sellerId=order.getSeller().getId(); State before=lock(sellerId);
        int inserted=jdbc.update("""
            INSERT INTO order_confirmation_penalties(order_id,milestone,seller_id,points)
            VALUES (?,?,?,?) ON CONFLICT(order_id,milestone) DO NOTHING
            """,order.getId(),milestone,sellerId,points);
        if(inserted==0) return false;
        jdbc.update("UPDATE seller_reputations SET confirmation_penalty_points=confirmation_penalty_points+?,updated_at=CURRENT_TIMESTAMP WHERE seller_id=?",points,sellerId);
        event(sellerId,"ORDER_CONFIRMATION_"+milestone,order.getId(),null,null,
            "Seller missed confirmation deadline; deducted "+points.toPlainString()+" points",before,state(sellerId,false).orElseThrow());
        return true;
    }

    private java.math.BigDecimal percentage(long count,long total) {
        return total==0?null:java.math.BigDecimal.valueOf(count).multiply(java.math.BigDecimal.valueOf(100)).divide(java.math.BigDecimal.valueOf(total),2,java.math.RoundingMode.HALF_UP);
    }

    @Transactional
    public ReputationResponse liftRestriction(UUID sellerId, UUID adminId, String reason) {
        get(sellerId);
        var before = lock(sellerId);
        if (before.restricted()) {
            jdbc.update("UPDATE seller_reputations SET selling_restricted=FALSE,updated_at=CURRENT_TIMESTAMP WHERE seller_id=?",sellerId);
            event(sellerId,"SELLING_RESTRICTION_LIFTED",null,null,adminId,reason.trim(),before,state(sellerId,false).orElseThrow());
        }
        return get(sellerId);
    }

    private State lock(UUID sellerId) {
        // Upsert avoids a race between the first two outcomes for a new seller. Never lock users here:
        // listing publication holds a user lock, while settlement already holds order/wallet locks.
        jdbc.update("INSERT INTO seller_reputations(seller_id) VALUES (?) ON CONFLICT (seller_id) DO NOTHING",sellerId);
        return state(sellerId,true).orElseThrow();
    }
    private Optional<State> state(UUID sellerId, boolean lock) {
        return jdbc.query("SELECT * FROM seller_reputations WHERE seller_id=?"+(lock?" FOR UPDATE":""),
                (rs,n)->new State(rs.getLong("good_transactions"),rs.getLong("bad_transactions"),rs.getLong("confirmed_incidents"),
                        rs.getLong("serious_incidents"),rs.getBoolean("selling_restricted"),instant(rs,"updated_at"),rs.getBigDecimal("confirmation_penalty_points")),sellerId).stream().findFirst();
    }
    private void event(UUID sellerId,String type,UUID orderId,UUID reportId,UUID actorId,String reason,State before,State after) {
        jdbc.update("""
            INSERT INTO seller_reputation_events(id,seller_id,event_type,order_id,report_id,actor_id,reason,
                good_before,bad_before,good_after,bad_after,score_before,score_after,formula_version,penalty_before,penalty_after,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
            """,UUID.randomUUID(),sellerId,type,orderId,reportId,actorId,reason,before.good(),before.bad(),after.good(),after.bad(),
                calculator.score(before.good(),before.bad(),before.penalty()),calculator.score(after.good(),after.bad(),after.penalty()),
                FORMULA_VERSION,before.penalty(),after.penalty());
    }
    private static Instant instant(ResultSet rs,String column) throws SQLException {
        var timestamp=rs.getTimestamp(column); return timestamp==null?null:timestamp.toInstant();
    }
}
