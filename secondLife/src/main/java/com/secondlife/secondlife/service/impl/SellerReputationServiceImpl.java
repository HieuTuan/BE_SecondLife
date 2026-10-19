package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.config.OrderConfirmationProperties;
import com.secondlife.secondlife.config.ReputationProperties;
import com.secondlife.secondlife.dto.request.ReportRequests;
import com.secondlife.secondlife.dto.response.OrderFulfillmentResponse;
import com.secondlife.secondlife.dto.response.ReportResponse;
import com.secondlife.secondlife.dto.response.ReputationHistoryResponse;
import com.secondlife.secondlife.dto.response.ReputationResponse;
import com.secondlife.secondlife.dto.response.SellerReputationData.Event;
import com.secondlife.secondlife.dto.response.SellerReputationData.State;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.mapper.OrderMapper;
import com.secondlife.secondlife.mapper.SellerReputationMapper;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.SellerReputationRepository;
import com.secondlife.secondlife.repository.ShipmentRepository;
import com.secondlife.secondlife.repository.ShippingQuoteRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.ReputationCalculator;
import com.secondlife.secondlife.service.SellerReputationService;


import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service("sellerReputationService")
public class SellerReputationServiceImpl implements SellerReputationService {

    private final SellerReputationRepository reputations;
    private final SellerReputationMapper reputationMapper;
    private final ShippingQuoteRepository shippingQuotes;
    private final ShipmentRepository shipments;
    private final UserRepository users;
    private final OrderRepository orders;
    private final ReputationProperties properties;
    private final ReputationCalculator calculator;
    private final OrderConfirmationProperties confirmations;
    private final OrderMapper orderMapper;

    public SellerReputationServiceImpl(SellerReputationRepository reputations,
                                      SellerReputationMapper reputationMapper,
                                      ShippingQuoteRepository shippingQuotes,
                                      ShipmentRepository shipments,
                                      UserRepository users,
                                      OrderRepository orders,
                                      ReputationProperties properties,
                                      ReputationCalculator calculator,
                                      OrderConfirmationProperties confirmations,
                                      OrderMapper orderMapper) {
        this.reputations = reputations;
        this.reputationMapper = reputationMapper;
        this.shippingQuotes = shippingQuotes;
        this.shipments = shipments;
        this.users = users;
        this.orders = orders;
        this.properties = properties;
        this.calculator = calculator;
        this.confirmations = confirmations;
        this.orderMapper = orderMapper;
    }

    public static final String FORMULA_VERSION="TRANSACTION_SUCCESS_RATE_V3";

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ReputationResponse get(UUID sellerId) {
        var state = state(sellerId, false).orElse(new State(0,0,0,0,false,null,BigDecimal.ZERO));
        requireSeller(sellerId, state);
        long evaluated = state.good() + state.bad();
        var metrics=reputationMapper.metrics(reputations.findMetrics(sellerId));
        long cancellations=metrics.cancellations(),returns=metrics.returns();
        long nfr=metrics.nfr(),handovers=metrics.handovers(),late=metrics.late();
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
        var state = state(sellerId, false).orElse(new State(0,0,0,0,false,null,BigDecimal.ZERO));
        requireSeller(sellerId, state);
        long count = reputations.countHistory(sellerId);
        var rows = reputations.findHistory(sellerId,page.getPageSize(),page.getOffset()).stream().map(reputationMapper::history).toList();
        return PageResponse.from(new PageImpl<>(rows,page,count));
    }

    private void requireSeller(UUID sellerId, State state) {
        var user = users.findByIdWithAuthorities(sellerId).orElseThrow(() -> new NotFoundException("Seller not found"));
        if (!user.hasRole("SELLER") && state.updated() == null) throw new NotFoundException("Seller not found");
    }

    public void requireSellingAllowed(UUID sellerId) {
        if (reputations.isSellingRestricted(sellerId)) throw new ForbiddenException("Seller is restricted following repeated confirmed fraud");
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
        var incidents=reputations.findIncidentSeverities(key);
        boolean first=incidents.isEmpty(),escalation=!first && serious && !incidents.getFirst();
        if(first) reputations.createIncident(key,sellerId,report.id(),serious);
        else if(escalation) reputations.escalateIncident(key,report.id());
        boolean faultAdded=order!=null && fault(order,faultType,report.id(),reviewerId,decisionReason);
        if(!first && !escalation && !faultAdded) return;
        int seriousIncrement=(first || escalation) && serious?1:0;
        boolean restrictSelling=seriousIncrement>0 && before.serious()+seriousIncrement>=properties.fraudRestrictionThreshold();
        reputations.incrementIncidentCounts(sellerId,first?1:0,seriousIncrement,restrictSelling);
        if(order!=null) evaluate(order);
        event(sellerId,escalation?"SERIOUS_VIOLATION_CONFIRMED":"SELLER_FAULT_CONFIRMED",report.orderId(),report.id(),reviewerId,
                decisionReason,before,state(sellerId,false).orElseThrow());
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void initializePaidOrder(Order order) {
        order.setPaidAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        order.setConfirmationDeadline(order.getPaidAt().plus(Duration.ofHours(confirmations.lateAfterHours())));
        order.setConfirmationCancelAt(order.getPaidAt().plus(Duration.ofHours(confirmations.cancelAfterHours())));
        order.setConfirmationLatePenalty(confirmations.latePenaltyPoints());
        order.setConfirmationTimeoutPenalty(confirmations.timeoutPenaltyPoints());
        // Inspection-center shipping is not a seller pickup and must not penalize seller readiness.
        String leg=shippingQuotes.findShippingQuoteLeg(order.getShippingQuoteId()).orElseThrow(() -> new EmptyResultDataAccessException(1));
        if("SELLER_TO_BUYER".equals(leg)) order.setHandoverDeadline(order.getPaidAt().plus(Duration.ofHours(properties.handoverDeadlineHours())));
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
        order.setSellerReadyAt(Instant.now().truncatedTo(ChronoUnit.MICROS)); orders.save(order); return fulfillment(order);
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
        return orderMapper.toFulfillmentResponse(order);
    }
    private boolean isReturn(Order order) {
        return order.getReturnResponsibleParty()!=null || shipments.hasReturnShipment(order.getId());
    }
    private boolean hasFault(UUID orderId) {
        return reputations.hasOrderFault(orderId);
    }
    private boolean fault(Order order,String type,UUID reportId,UUID actorId,String reason) {
        return reputations.addOrderFaultIfAbsent(order.getId(),order.getSeller().getId(),type,reportId,actorId,reason)==1;
    }
    private boolean evaluate(Order order) {
        boolean bad=hasFault(order.getId()); String next=null;
        if(order.getStatus()==OrderStatus.COMPLETED && order.getEscrowStatus()==EscrowStatus.RELEASED) next=bad?"BAD":"GOOD";
        else if(order.getStatus()==OrderStatus.CANCELLED && order.getEscrowStatus()==EscrowStatus.REFUNDED && bad) next="BAD";
        String old=reputations.findOrderOutcome(order.getId());
        if(Objects.equals(old,next)) return false;
        if(next==null) {
            // No pending order contributes to N. Closed non-seller cancellations are excluded as well.
            reputations.deleteOrderOutcome(order.getId());
        } else {
            reputations.saveOrderOutcome(order.getId(),order.getSeller().getId(),next);
        }
        reputations.adjustTransactionCounts(order.getSeller().getId(),
                ("GOOD".equals(next)?1:0)-("GOOD".equals(old)?1:0),("BAD".equals(next)?1:0)-("BAD".equals(old)?1:0));
        return true;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public boolean recordConfirmationPenalty(Order order,String milestone,BigDecimal points) {
        if(!Set.of("LATE","TIMEOUT").contains(milestone) || points==null || points.signum()<=0)
            throw new IllegalArgumentException("Invalid confirmation penalty");
        UUID sellerId=order.getSeller().getId(); State before=lock(sellerId);
        int inserted=reputations.addConfirmationPenaltyIfAbsent(order.getId(),milestone,sellerId,points);
        if(inserted==0) return false;
        reputations.increaseConfirmationPenalty(sellerId,points);
        event(sellerId,"ORDER_CONFIRMATION_"+milestone,order.getId(),null,null,
            "Seller missed confirmation deadline; deducted "+points.toPlainString()+" points",before,state(sellerId,false).orElseThrow());
        return true;
    }

    private BigDecimal percentage(long count,long total) {
        return total==0?null:BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total),2,RoundingMode.HALF_UP);
    }

    @Transactional
    public ReputationResponse liftRestriction(UUID sellerId, UUID adminId, String reason) {
        get(sellerId);
        var before = lock(sellerId);
        if (before.restricted()) {
            reputations.liftRestriction(sellerId);
            event(sellerId,"SELLING_RESTRICTION_LIFTED",null,null,adminId,reason.trim(),before,state(sellerId,false).orElseThrow());
        }
        return get(sellerId);
    }

    private State lock(UUID sellerId) {
        // Upsert avoids a race between the first two outcomes for a new seller. Never lock users here:
        // listing publication holds a user lock, while settlement already holds order/wallet locks.
        reputations.initializeSellerState(sellerId);
        return state(sellerId,true).orElseThrow();
    }
    private Optional<State> state(UUID sellerId, boolean lock) {
        return (lock?reputations.findStateForUpdate(sellerId):reputations.findState(sellerId)).map(reputationMapper::state);
    }
    private void event(UUID sellerId,String type,UUID orderId,UUID reportId,UUID actorId,String reason,State before,State after) {
        reputations.appendEvent(new Event(UUID.randomUUID(),sellerId,type,orderId,reportId,actorId,reason,before,after,
                calculator.score(before.good(),before.bad(),before.penalty()),calculator.score(after.good(),after.bad(),after.penalty()),
                FORMULA_VERSION));
    }

}
