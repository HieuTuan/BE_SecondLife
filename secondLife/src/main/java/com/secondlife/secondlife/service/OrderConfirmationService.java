package com.secondlife.secondlife.service;

import com.secondlife.secondlife.config.OrderConfirmationProperties;
import com.secondlife.secondlife.dto.reputation.OrderConfirmationResponse;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.repository.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class OrderConfirmationService {
    private final OrderRepository orders;
    private final ShipmentRepository shipments;
    private final PostRepository posts;
    private final SystemWalletService systemWallet;
    private final WalletService wallets;
    private final SellerReputationService reputation;
    private final NotificationService notifications;
    private final OrderConfirmationProperties properties;
    private final Clock clock;

    public OrderConfirmationService(OrderRepository orders,ShipmentRepository shipments,PostRepository posts,
            SystemWalletService systemWallet,WalletService wallets,SellerReputationService reputation,
            NotificationService notifications,OrderConfirmationProperties properties,
            @Qualifier("orderConfirmationClock") Clock clock) {
        this.orders=orders;this.shipments=shipments;this.posts=posts;this.systemWallet=systemWallet;
        this.wallets=wallets;this.reputation=reputation;this.notifications=notifications;this.properties=properties;this.clock=clock;
    }
    public Instant now() { return clock.instant(); }
    @Transactional
    public OrderConfirmationResponse confirm(UUID sellerId,UUID orderId) {
        var o=orders.findByIdForUpdate(orderId).orElseThrow(()->new NotFoundException("Order not found"));
        if(!o.getSeller().getId().equals(sellerId)) throw new ForbiddenException("Only the owning seller can confirm this order");
        if(o.getSellerConfirmedAt()!=null) return OrderConfirmationResponse.from(o);
        if(o.getStatus()!=OrderStatus.PROCESSING || o.getEscrowStatus()!=EscrowStatus.HELD || o.getPaidAt()==null)
            throw new ConflictException("Only a paid, unshipped order with HELD escrow can be confirmed");
        var now=now();
        if(o.getConfirmationCancelAt()!=null && !now.isBefore(o.getConfirmationCancelAt()))
            throw new ConflictException("Confirmation cancellation deadline has passed");
        if(o.getConfirmationDeadline()!=null && !now.isBefore(o.getConfirmationDeadline())) penalize(o,"LATE");
        o.setSellerConfirmedAt(now.truncatedTo(java.time.temporal.ChronoUnit.MICROS));o.setStatus(OrderStatus.CONFIRMED);
        orders.saveAndFlush(o);
        notifications.notify(o.getBuyer().getId(),"Người bán đã xác nhận đơn hàng",
            "Đơn hàng #"+o.getId()+" đã được người bán xác nhận và sẽ được chuẩn bị giao.","ORDER",o.getId(),"ORDER","/buyer/orders/"+o.getId());
        return OrderConfirmationResponse.from(o);
    }
    @Transactional(readOnly=true)
    public OrderConfirmationResponse get(UUID actor,boolean reviewer,UUID id) {
        var o=orders.findById(id).orElseThrow(()->new NotFoundException("Order not found"));
        if(!reviewer && !o.getSeller().getId().equals(actor) && !o.getBuyer().getId().equals(actor))
            throw new ForbiddenException("This order belongs to another user");
        return OrderConfirmationResponse.from(o);
    }
    @Transactional(readOnly=true)
    public Page<OrderConfirmationResponse> queue(OrderStatus status,Pageable page) {
        var safe=PageRequest.of(page.getPageNumber(),page.getPageSize(),Sort.by(Sort.Direction.DESC,"paidAt","id"));
        return (status==null?orders.findByConfirmationDeadlineIsNotNull(safe):
            orders.findByConfirmationDeadlineIsNotNullAndStatus(status,safe)).map(OrderConfirmationResponse::from);
    }
    @Transactional
    public boolean processIfDue(UUID id) {
        var found=orders.findByIdForUpdate(id);
        if(found.isEmpty()) return false;
        var o=found.get();
        if(o.getStatus()!=OrderStatus.PROCESSING || o.getEscrowStatus()!=EscrowStatus.HELD
                || o.getSellerConfirmedAt()!=null || o.getPaidAt()==null
                || o.getConfirmationDeadline()==null || o.getConfirmationCancelAt()==null
                || o.getShippingDeliveredAt()!=null || o.getCarrierPickedUpAt()!=null) return false;
        var now=now();
        if(now.isBefore(o.getConfirmationDeadline())) return false;
        var shipping=shipments.findByOrderIdOrderByCreatedAtAsc(id);
        // Never refund while a carrier booking is active or its creation is uncertain.
        if(shipping.stream().anyMatch(x->!"CANCELLED".equals(x.getStatus()))) return false;
        if(now.isBefore(o.getConfirmationCancelAt())) return penalize(o,"LATE");

        var refund=o.getFinalPrice();
        if(shipping.stream().allMatch(x->x.getOrderCode()==null)) refund=refund.add(o.getShippingFee());
        // Keep order -> system wallet -> buyer wallet -> reputation lock ordering, like ordinary cancellation.
        systemWallet.refund(o,refund);
        wallets.processRefund(o.getBuyer().getId(),refund,id);
        o.setStatus(OrderStatus.CANCELLED);o.setEscrowStatus(EscrowStatus.REFUNDED);
        o.setCancellationReasonCode("SELLER_CONFIRMATION_TIMEOUT");o.setCancelledBy(null);
        orders.saveAndFlush(o);
        penalize(o,"LATE");penalize(o,"TIMEOUT");
        reputation.recordSellerCancellation(o,null,"Automatic cancellation: seller did not confirm before "+o.getConfirmationCancelAt());
        var post=posts.findByIdForUpdate(o.getPost().getId()).orElseThrow(()->new NotFoundException("Post not found"));
        if("SOLD".equals(post.getStatus())) { post.setStatus("ACTIVE");posts.save(post); }
        notifications.notifyWithEmail(o.getBuyer().getId(),"Đơn hàng đã tự động hủy và hoàn tiền",
            "Đơn hàng #"+id+" bị hủy vì người bán chưa xác nhận trước "+o.getConfirmationCancelAt()+
            ". Đã hoàn "+refund.toPlainString()+" VND vào ví của bạn.","ORDER",id,"ORDER","/buyer/orders/"+id);
        notifications.notify(o.getSeller().getId(),"Đơn hàng đã tự động hủy",
            "Đơn hàng #"+id+" đã bị hủy do quá hạn xác nhận. Điểm phạt và kết quả BAD được lưu trong lịch sử uy tín.",
            "ORDER",id,"ORDER","/seller/orders/"+id);
        return true;
    }

    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public boolean recordLateCancellationIfDue(Order o,Instant cancelledAt) {
        // The caller already refunded under wallet locks; do not acquire them after locking reputation.
        if(o.getSellerConfirmedAt()!=null || o.getPaidAt()==null || o.getConfirmationDeadline()==null
                || o.getConfirmationCancelAt()==null || cancelledAt.isBefore(o.getConfirmationDeadline())
                || !cancelledAt.isBefore(o.getConfirmationCancelAt())) return false;
        return penalize(o,"LATE");
    }

    private boolean penalize(Order o,String milestone) {
        var points="LATE".equals(milestone)?o.getConfirmationLatePenalty():o.getConfirmationTimeoutPenalty();
        if(!reputation.recordConfirmationPenalty(o,milestone,points)) return false;
        notifications.notifyWithEmail(o.getSeller().getId(),"Bị trừ điểm uy tín do chưa xác nhận đơn",
            "Đơn hàng #"+o.getId()+" quá hạn xác nhận. Bạn bị trừ "+points.toPlainString()+
            " điểm uy tín. Hạn hủy tự động: "+o.getConfirmationCancelAt(),"REPUTATION",o.getId(),"ORDER","/seller/orders/"+o.getId());
        return true;
    }
}
