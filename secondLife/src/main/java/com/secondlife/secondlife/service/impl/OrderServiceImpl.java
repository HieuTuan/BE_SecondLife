package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.common.PageableUtils;
import com.secondlife.secondlife.dto.request.CancelOrderRequest;
import com.secondlife.secondlife.dto.request.OrderRequestDTO;
import com.secondlife.secondlife.dto.response.OrderResponseDTO;
import com.secondlife.secondlife.entity.Negotiation;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.NegotiationStatus;
import com.secondlife.secondlife.enums.OrderPaymentMethod;
import com.secondlife.secondlife.enums.OrderStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.OrderMapper;
import com.secondlife.secondlife.repository.NegotiationRepository;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.ShipmentRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.CommissionService;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.OrderBankPaymentService;
import com.secondlife.secondlife.service.OrderConfirmationService;
import com.secondlife.secondlife.service.OrderService;
import com.secondlife.secondlife.service.PaidOrderService;
import com.secondlife.secondlife.service.SellerReputationService;
import com.secondlife.secondlife.service.SettlementService;
import com.secondlife.secondlife.service.ShippingQuoteService;
import com.secondlife.secondlife.service.SystemWalletService;
import com.secondlife.secondlife.service.WalletService;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orderMapper;

    private static final Set<String> ALLOWED_ORDER_SORT_PROPERTIES = Set.of(
            "id", "finalPrice", "status", "escrowStatus", "shippingFee", "createdAt", "updatedAt", "shippingDeliveredAt"
    );
    private static final Sort DEFAULT_ORDER_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final OrderRepository orderRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NegotiationRepository negotiationRepository;
    private final WalletService walletService;
    private final ShippingQuoteService shippingQuotes;
    private final ShipmentRepository shipments;
    private final CommissionService commissions;
    private final SettlementService settlements;
    private final SystemWalletService systemWallet;
    private final SellerReputationService reputation;
    private final NotificationService notificationService;
    private final OrderConfirmationService confirmations;
    private PaidOrderService paidOrders;
    private OrderBankPaymentService bankPayments;

    @Autowired
    public OrderServiceImpl(OrderRepository orders,PostRepository posts,UserRepository users,NegotiationRepository negotiations,
            WalletService wallets,ShippingQuoteService quotes,ShipmentRepository shipments,CommissionService commissions,
            SettlementService settlements,SystemWalletService systemWallet,SellerReputationService reputation,
            NotificationService notifications,OrderConfirmationService confirmations,
            PaidOrderService paidOrders,
            OrderBankPaymentService bankPayments, OrderMapper orderMapper) {
        this(orders,posts,users,negotiations,wallets,quotes,shipments,commissions,settlements,systemWallet,reputation,notifications,confirmations, orderMapper);
        this.paidOrders=paidOrders; this.bankPayments=bankPayments;
    }

    public OrderServiceImpl(OrderRepository orderRepository,PostRepository postRepository,UserRepository userRepository,
            NegotiationRepository negotiationRepository,WalletService walletService,ShippingQuoteService shippingQuotes,
            ShipmentRepository shipments,CommissionService commissions,SettlementService settlements,SystemWalletService systemWallet,
            SellerReputationService reputation,
            NotificationService notificationService, OrderMapper orderMapper) {
        this(orderRepository,postRepository,userRepository,negotiationRepository,walletService,shippingQuotes,shipments,
                commissions,settlements,systemWallet,reputation,notificationService,null, orderMapper);
    }

    public OrderServiceImpl(
            OrderRepository orderRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            NegotiationRepository negotiationRepository,
            WalletService walletService,
            ShippingQuoteService shippingQuotes,
            ShipmentRepository shipments,
            CommissionService commissions,
            SettlementService settlements,
            SystemWalletService systemWallet,
            SellerReputationService reputation,
            NotificationService notificationService,
            OrderConfirmationService confirmations
    , OrderMapper orderMapper) {
        this.orderMapper = orderMapper;

        this.orderRepository = orderRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.negotiationRepository = negotiationRepository;
        this.walletService = walletService;
        this.shippingQuotes = shippingQuotes;
        this.shipments = shipments;
        this.commissions = commissions;
        this.settlements = settlements;
        this.systemWallet = systemWallet;
        this.reputation = Objects.requireNonNull(reputation, "SellerReputationService is required");
        this.notificationService = notificationService;
        this.confirmations = confirmations;
        this.paidOrders = new PaidOrderServiceImpl(orderRepository,postRepository,
            negotiationRepository,walletService,systemWallet,reputation,notificationService,userRepository);
    }

    public OrderServiceImpl(
            OrderRepository orderRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            NegotiationRepository negotiationRepository,
            WalletService walletService,
            ShippingQuoteService shippingQuotes,
            ShipmentRepository shipments,
            CommissionService commissions,
            SettlementService settlements,
            SystemWalletService systemWallet,
            SellerReputationService reputation
    , OrderMapper orderMapper) {
        this(orderRepository, postRepository, userRepository, negotiationRepository, walletService, shippingQuotes, shipments, commissions, settlements, systemWallet, reputation, null, orderMapper);
    }

    @Override
    @Transactional
    public OrderResponseDTO createOrder(UUID buyerId, OrderRequestDTO requestDTO) {
        if (requestDTO.requestId()==null) throw new BadRequestException("requestId is required for safe order retries");
        Post post = postRepository.findByIdForUpdate(requestDTO.postId())
                .orElseThrow(() -> new NotFoundException("Post not found"));

        var replay=orderRepository.findByBuyerIdAndRequestId(buyerId,requestDTO.requestId());
        if (replay.isPresent()) {
            var previous=replay.get();
            if (!previous.getPost().getId().equals(requestDTO.postId())
                    || previous.getPaymentMethod()!=requestDTO.paymentMethod()
                    || !Objects.equals(previous.getShippingQuoteId(),requestDTO.shippingQuoteId())
                    || !Objects.equals(previous.getNegotiation()==null?null:previous.getNegotiation().getId(),requestDTO.negotiationId()))
                throw new ConflictException("requestId was already used with different order details");
            return mapToDTO(previous);
        }

        reputation.requireSellingAllowed(post.getUser().getId());

        if (!"ACTIVE".equals(post.getStatus()) && !"RESERVED".equals(post.getStatus())) {
            throw new ConflictException("Post is not available for purchase");
        }

        if ("RESERVED".equals(post.getStatus()) && requestDTO.negotiationId() == null) {
            throw new ConflictException("Post is reserved. You must provide a valid negotiation to purchase it.");
        }

        if (post.getUser().getId().equals(buyerId)) {
            throw new BadRequestException("You cannot buy your own post");
        }

        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new NotFoundException("Buyer not found"));

        BigDecimal finalPrice = post.getPrice();
        Negotiation negotiation = null;

        if (requestDTO.negotiationId() != null) {
            negotiation = negotiationRepository.findById(requestDTO.negotiationId())
                    .orElseThrow(() -> new NotFoundException("Negotiation not found"));

            if (!negotiation.getBuyer().getId().equals(buyerId) || !negotiation.getPost().getId().equals(post.getId())) {
                throw new BadRequestException("Invalid negotiation for this order");
            }

            if (negotiation.getStatus() != NegotiationStatus.ACCEPTED) {
                throw new BadRequestException("Negotiation is not ACCEPTED");
            }

            if (negotiation.getExpiredAt() != null && negotiation.getExpiredAt().isBefore(Instant.now())) {
                // Lazy rollback
                negotiation.setStatus(NegotiationStatus.EXPIRED);
                negotiationRepository.save(negotiation);
                post.setStatus("ACTIVE");
                postRepository.save(post);
                throw new BadRequestException("Negotiation has expired. The product is available again. Please re-negotiate if you still want to buy it.");
            }

            finalPrice = negotiation.getOfferedPrice();
        }

        // Process payment and hold in escrow
        Order order = new Order();
        order.setPost(post);
        order.setBuyer(buyer);
        order.setSeller(post.getUser());
        order.setNegotiation(negotiation);
        order.setFinalPrice(finalPrice);
        order.setRequestId(requestDTO.requestId());
        order.setPaymentMethod(requestDTO.paymentMethod());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setEscrowStatus(EscrowStatus.PENDING);

        order = orderRepository.saveAndFlush(order);
        commissions.capture(order, buyerId, "Order created");
        var shippingQuote=shippingQuotes.consume(buyerId,post.getId(),requestDTO.shippingQuoteId(),order.getId(),finalPrice);
        order.setShippingQuoteId(shippingQuote.getId()); order.setShippingFee(shippingQuote.getFee()); order.setDeliveryAddress(shippingQuote.getDeliveryAddress());
        orderRepository.save(order);

        if (requestDTO.paymentMethod()==OrderPaymentMethod.BANK_TRANSFER) {
            bankPayments.begin(order);
        } else {
            paidOrders.complete(order,true);
        }
        return mapToDTO(order);
    }

    @Override
    @Transactional
    public OrderResponseDTO markAsShipped(UUID sellerId, UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));

        if (!order.getSeller().getId().equals(sellerId)) {
            throw new BadRequestException("Only the seller can mark the order as shipped");
        }

        if (order.getStatus() != OrderStatus.PROCESSING && order.getStatus()!=OrderStatus.CONFIRMED) {
            throw new BadRequestException("Order is not in PROCESSING state");
        }

        throw new ConflictException("Shipment status is updated from Carrier; create a shipment and wait for carrier pickup");
    }

    @Override
    @Transactional
    public OrderResponseDTO confirmDelivery(UUID buyerId, UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));

        if (!order.getBuyer().getId().equals(buyerId)) {
            throw new BadRequestException("Only the buyer can confirm delivery");
        }

        if (order.getStatus()==OrderStatus.COMPLETED && order.getEscrowStatus()==EscrowStatus.RELEASED) return mapToDTO(order);
        if (order.getStatus()!=OrderStatus.DELIVERED || order.getShippingDeliveredAt()==null)
            throw new ConflictException("Carrier must confirm delivery before the buyer can release escrow");

        if (order.getEscrowStatus() != EscrowStatus.HELD) {
            throw new ConflictException("Escrow funds are not currently HELD; resolve the shipping issue or return decision first");
        }

        // Persist settlement and release the net seller amount using the order's policy snapshot.
        settlements.settle(order);

        if (notificationService != null) {
            notificationService.notify(
                    order.getBuyer().getId(),
                    "Đơn hàng hoàn tất",
                    "Cảm ơn bạn đã xác nhận đã nhận hàng thành công cho đơn hàng #" + order.getId() + ".",
                    "ORDER",
                    order.getId(),
                    "ORDER",
                    "/buyer/orders/" + order.getId()
            );
            notificationService.notifyWithEmail(
                    order.getSeller(),
                    "Tiền bán hàng đã về ví!",
                    "Đơn hàng #" + order.getId() + " đã hoàn tất. Doanh thu bán hàng đã được giải ngân vào ví SecondLife của bạn.",
                    "ORDER",
                    order.getId(),
                    "ORDER",
                    "/wallet",
                    "Giải ngân tiền bán hàng đơn #" + order.getId() + " - SecondLife",
                    "Đơn hàng #" + order.getId() + " đã hoàn tất thành công. Doanh thu bán hàng đã được giải ngân vào ví SecondLife của bạn."
            );
        }

        return mapToDTO(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponseDTO cancelOrder(UUID userId, UUID orderId) { return cancelOrder(userId,orderId,null); }

    @Override @Transactional
    public OrderResponseDTO cancelOrder(UUID userId,UUID orderId,CancelOrderRequest request) {
        var initial=orderRepository.findById(orderId).orElseThrow(() -> new NotFoundException("Order not found"));
        if (initial.getPaymentMethod()==OrderPaymentMethod.BANK_TRANSFER
                && initial.getPaidAt()==null) return mapToDTO(bankPayments.cancel(userId,orderId));
        postRepository.findByIdForUpdate(initial.getPost().getId()).orElseThrow(() -> new NotFoundException("Post not found"));
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        orderRepository.refreshManaged(order, LockModeType.PESSIMISTIC_WRITE);

        if (!order.getBuyer().getId().equals(userId) && !order.getSeller().getId().equals(userId)) {
            throw new BadRequestException("You are not authorized to cancel this order");
        }

        var cancellationAt=confirmations==null?Instant.now():confirmations.now();
        if(confirmations!=null && order.getSellerConfirmedAt()==null && order.getConfirmationCancelAt()!=null
                && !cancellationAt.isBefore(order.getConfirmationCancelAt()) && confirmations.processIfDue(orderId)) {
            return mapToDTO(order);
        }
        boolean sellerCancel=order.getSeller().getId().equals(userId);
        if(sellerCancel && request==null) throw new BadRequestException("Seller cancellation requires reasonCode and reason");
        var code=request==null?CancelOrderRequest.ReasonCode.BUYER_REQUESTED:request.reasonCode();
        if(sellerCancel == (code==CancelOrderRequest.ReasonCode.BUYER_REQUESTED))
            throw new BadRequestException("Cancellation reason must match the authenticated buyer or seller");
        if(order.getStatus()==OrderStatus.CANCELLED && order.getEscrowStatus()==EscrowStatus.REFUNDED) {
            if(order.getCancelledBy()!=null && (!order.getCancelledBy().equals(userId) || !code.name().equals(order.getCancellationReasonCode())))
                throw new ConflictException("Order cancellation was already finalized by another actor or reason");
            return mapToDTO(order);
        }
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Order cannot be cancelled in current status");
        }

        if (order.getEscrowStatus() != EscrowStatus.HELD) {
            throw new BadRequestException("Escrow funds are not HELD. Cannot cancel and refund.");
        }
        var shipping=shipments.findByOrderIdOrderByCreatedAtAsc(orderId);
        if (shipping.stream().anyMatch(s -> !"CANCELLED".equals(s.getStatus())) || (order.getStatus()!=OrderStatus.PROCESSING && order.getStatus()!=OrderStatus.CONFIRMED))
            throw new ConflictException("Cancel the carrier shipment before cancelling the order; shipped/delivered goods require return review");

        // Refund buyer
        // Carrier-created shipments may incur pickup/cancellation charges; don't silently refund a consumed shipping fee.
        BigDecimal refund=order.getFinalPrice();
        if (shipping.stream().allMatch(s -> s.getOrderCode()==null)) refund=refund.add(order.getShippingFee());
        systemWallet.refund(order, refund);
        walletService.processRefund(order.getBuyer().getId(), refund, order.getId());
        if(confirmations!=null) confirmations.recordLateCancellationIfDue(order,cancellationAt);

        order.setStatus(OrderStatus.CANCELLED);
        order.setEscrowStatus(EscrowStatus.REFUNDED);
        order.setCancelledBy(userId); order.setCancellationReasonCode(code.name());
        orderRepository.saveAndFlush(order);
        if(sellerCancel) reputation.recordSellerCancellation(order,userId,request.reason().trim());
        else reputation.recordClosedOrder(order,userId,"Buyer cancellation",false);

        // Revert post status to ACTIVE
        Post post = postRepository.findByIdForUpdate(order.getPost().getId()).orElseThrow();
        post.setStatus("ACTIVE");
        postRepository.save(post);

        if (notificationService != null) {
            notificationService.notifyWithEmail(
                    order.getBuyer(),
                    "Đơn hàng đã bị hủy",
                    "Đơn hàng #" + order.getId() + " đã bị hủy. Khoản thanh toán " + refund + " VND đã được hoàn lại vào ví của bạn.",
                    "ORDER",
                    order.getId(),
                    "ORDER",
                    "/wallet",
                    "Thông báo hủy đơn hàng #" + order.getId() + " và hoàn tiền - SecondLife",
                    "Đơn hàng #" + order.getId() + " đã bị hủy. Số tiền " + refund + " VND đã được hoàn trả về ví SecondLife của bạn."
            );
            notificationService.notifyWithEmail(
                    order.getSeller(),
                    "Đơn hàng đã bị hủy",
                    "Đơn hàng #" + order.getId() + " của sản phẩm '" + order.getPost().getTitle() + "' đã bị hủy.",
                    "ORDER",
                    order.getId(),
                    "ORDER",
                    "/seller/orders/" + order.getId(),
                    "Thông báo hủy đơn hàng #" + order.getId() + " - SecondLife",
                    "Đơn hàng #" + order.getId() + " của sản phẩm '" + order.getPost().getTitle() + "' đã bị hủy."
            );
        }

        return mapToDTO(orderRepository.save(order));
    }

    @Override
    public Page<OrderResponseDTO> getBuyerOrders(UUID buyerId, Pageable pageable) {
        Pageable safePageable = PageableUtils.sanitize(pageable, ALLOWED_ORDER_SORT_PROPERTIES, DEFAULT_ORDER_SORT);
        return orderRepository.findByBuyerId(buyerId, safePageable).map(this::mapToDTO);
    }

    @Override
    public Page<OrderResponseDTO> getSellerOrders(UUID sellerId, Pageable pageable) {
        Pageable safePageable = PageableUtils.sanitize(pageable, ALLOWED_ORDER_SORT_PROPERTIES, DEFAULT_ORDER_SORT);
        return orderRepository.findBySellerId(sellerId, safePageable).map(this::mapToDTO);
    }

    private OrderResponseDTO mapToDTO(Order order) {
        BigDecimal totalAmount = order.getFinalPrice().add(order.getShippingFee());
        var payment = order.getPaymentMethod() == OrderPaymentMethod.BANK_TRANSFER
                ? bankPayments.details(order) : null;
        return orderMapper.toResponse(order, payment, totalAmount);
    }
}
