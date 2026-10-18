package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.config.OrderBankTransferProperties;
import com.secondlife.secondlife.dto.request.SePayWebhookDTO;
import com.secondlife.secondlife.dto.response.OrderBankPaymentResponse;
import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.mapper.OrderMapper;
import jakarta.persistence.LockModeType;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.OrderBankPaymentService;
import com.secondlife.secondlife.service.PaidOrderService;
import com.secondlife.secondlife.service.WalletService;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.*;
import java.util.UUID;

@Service("orderBankPaymentService")
public class OrderBankPaymentServiceImpl implements OrderBankPaymentService {
    private final OrderMapper orderMapper;

    private final OrderRepository orders;
    private final PostRepository posts;
    private final NegotiationRepository negotiations;
    private final DepositRequestRepository requests;
    private final PaidOrderService paidOrders;
    private final WalletService wallets;
    private final SePayTransferLedger ledger;
    private final OrderBankTransferProperties properties;
    private final Clock clock;
    private final String bankAccountName,bankAccountNumber,bankName;
    private final SecureRandom random=new SecureRandom();
    public OrderBankPaymentServiceImpl(OrderRepository orders,PostRepository posts,NegotiationRepository negotiations,
            DepositRequestRepository requests,PaidOrderService paidOrders,WalletService wallets,SePayTransferLedger ledger,
            OrderBankTransferProperties properties,@Qualifier("bankPaymentClock") Clock clock,
            @Value("${app.sepay.bank-account-name}") String bankAccountName,
            @Value("${app.sepay.bank-account-number}") String bankAccountNumber,
            @Value("${app.sepay.bank-name}") String bankName, OrderMapper orderMapper) {
        this.orderMapper = orderMapper;

        this.orders=orders;this.posts=posts;this.negotiations=negotiations;this.requests=requests;this.paidOrders=paidOrders;
        this.wallets=wallets;this.ledger=ledger;this.properties=properties;this.clock=clock;
        this.bankAccountName=bankAccountName;this.bankAccountNumber=bankAccountNumber;this.bankName=bankName;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void begin(Order order) {
        if(bankAccountName.isBlank() || bankAccountNumber.isBlank() || bankName.isBlank())
            throw new ConflictException("Bank transfer payment is not configured");
        var request=new DepositRequest();request.setOrderId(order.getId());request.setUser(order.getBuyer());
        request.setAmount(order.getFinalPrice().add(order.getShippingFee()));
        request.setExpiresAt(clock.instant().plus(Duration.ofMinutes(properties.timeoutMinutes())));
        String code;
        do { code="SCL"+(100000+random.nextInt(900000)); } while(requests.existsByCode(code));
        request.setCode(code);requests.saveAndFlush(request);
        order.getPost().setStatus("PAYMENT_PENDING");posts.save(order.getPost());
    }
    // Consistent lock order: post -> order -> system wallet -> user wallet. The order lock also serializes its payment request.
    private Order lock(UUID orderId) {
        var postId=orders.findPostId(orderId).orElseThrow(()->new NotFoundException("Order not found"));
        var post=posts.findByIdForUpdate(postId).orElseThrow(()->new NotFoundException("Post not found"));
        posts.refreshManaged(post, LockModeType.PESSIMISTIC_WRITE);
        var order=orders.findByIdForUpdate(orderId).orElseThrow(()->new NotFoundException("Order not found"));
        // Cancellation may have loaded this order earlier in the same persistence context.
        orders.refreshManaged(order, LockModeType.PESSIMISTIC_WRITE);
        return order;
    }
    @Transactional
    public void receive(UUID orderId,SePayWebhookDTO event,String code) {
        var order=lock(orderId);var request=requests.findByOrderId(orderId).orElseThrow();
        if(!request.getCode().equals(code)) throw new BadRequestException("Payment reference does not match this order");
        if(!ledger.claim(event,code)) return;
        request.setReceivedAmount(request.getReceivedAmount().add(event.transferAmount()));
        if(order.getStatus()==OrderStatus.PENDING_PAYMENT && order.getPaidAt()==null) {
            if(!clock.instant().isBefore(request.getExpiresAt()) || !"PAYMENT_PENDING".equals(order.getPost().getStatus())) {
                close(order,"PAYMENT_EXPIRED");
            } else if(request.getReceivedAmount().compareTo(request.getAmount())>=0) {
                // Bank money directly funds escrow. Never debit the buyer's personal wallet here.
                paidOrders.complete(order,false);
                request.setAllocatedAmount(request.getAmount());request.setStatus(DepositStatus.COMPLETED);
            }
        }
        creditUnallocatedIfClosed(order,request);
        requests.save(request);orders.save(order);
    }
    private void creditUnallocatedIfClosed(Order order,DepositRequest request) {
        if(order.getStatus()==OrderStatus.PENDING_PAYMENT) return;
        var credit=request.getReceivedAmount().subtract(request.getAllocatedAmount()).subtract(request.getWalletCreditedAmount());
        if(credit.signum()>0) {
            wallets.deposit(order.getBuyer().getId(),credit,request.getId());
            request.setWalletCreditedAmount(request.getWalletCreditedAmount().add(credit));
        }
    }
    private void close(Order order,String reason) {
        order.setStatus(OrderStatus.CANCELLED);order.setEscrowStatus(EscrowStatus.PENDING);
        order.setCancellationReasonCode(reason);
        if("PAYMENT_PENDING".equals(order.getPost().getStatus())) {
            var negotiation=order.getNegotiation();String restored="ACTIVE";
            if(negotiation!=null && negotiation.getStatus()==NegotiationStatus.ACCEPTED) {
                if(negotiation.getExpiredAt()!=null && clock.instant().isBefore(negotiation.getExpiredAt())) restored="RESERVED";
                else { negotiation.setStatus(NegotiationStatus.EXPIRED);negotiations.save(negotiation); }
            }
            order.getPost().setStatus(restored);posts.save(order.getPost());
        }
    }
    @Transactional
    public Order cancel(UUID actorId,UUID orderId) {
        var order=lock(orderId);
        if(!order.getBuyer().getId().equals(actorId)) throw new ForbiddenException("Only the buyer can cancel an unpaid bank-transfer order");
        if(order.getPaidAt()!=null || order.getStatus()!=OrderStatus.PENDING_PAYMENT && order.getStatus()!=OrderStatus.CANCELLED)
            throw new ConflictException("Order payment changed; refresh the order before cancelling");
        if(order.getStatus()==OrderStatus.PENDING_PAYMENT) {
            close(order,"BUYER_CANCELLED_UNPAID");order.setCancelledBy(actorId);
            var request=requests.findByOrderId(orderId).orElseThrow();creditUnallocatedIfClosed(order,request);requests.save(request);
            orders.save(order);
        }
        return order;
    }
    @Transactional
    public void expire(UUID orderId) {
        var order=lock(orderId);var request=requests.findByOrderId(orderId).orElseThrow();
        if(order.getStatus()!=OrderStatus.PENDING_PAYMENT || clock.instant().isBefore(request.getExpiresAt())) return;
        close(order,"PAYMENT_EXPIRED");creditUnallocatedIfClosed(order,request);requests.save(request);orders.save(order);
    }
    @Transactional(readOnly=true)
    public OrderBankPaymentResponse get(UUID buyerId,UUID orderId) {
        var order=orders.findById(orderId).orElseThrow(()->new NotFoundException("Order not found"));
        if(!order.getBuyer().getId().equals(buyerId)) throw new ForbiddenException("Payment instructions belong to another buyer");
        return details(order);
    }
    @Transactional(readOnly=true)
    public OrderBankPaymentResponse details(Order order) {
        var r=requests.findByOrderId(order.getId()).orElseThrow(()->new NotFoundException("Bank payment not found"));
        String status=r.getAllocatedAmount().signum()>0?"PAID":order.getStatus()==OrderStatus.CANCELLED?
            ("PAYMENT_EXPIRED".equals(order.getCancellationReasonCode())?"EXPIRED":"CANCELLED"):
            !clock.instant().isBefore(r.getExpiresAt())?"EXPIRED":r.getReceivedAmount().signum()>0?"PARTIALLY_PAID":"PENDING";
        var remaining=("PENDING".equals(status)||"PARTIALLY_PAID".equals(status))?
            r.getAmount().subtract(r.getReceivedAmount()).max(BigDecimal.ZERO):BigDecimal.ZERO;
        return orderMapper.toBankPaymentResponse(order, r, status, remaining, bankAccountName, bankAccountNumber, bankName);
    }
}
