package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.OrderRequestDTO;
import com.secondlife.secondlife.dto.response.OrderResponseDTO;
import com.secondlife.secondlife.entity.Negotiation;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.NegotiationStatus;
import com.secondlife.secondlife.enums.OrderStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.repository.ShipmentRepository;
import com.secondlife.secondlife.service.shipping.ShippingQuoteService;
import com.secondlife.secondlife.repository.NegotiationRepository;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.OrderService;
import com.secondlife.secondlife.service.WalletService;
import com.secondlife.secondlife.service.CommissionService;
import com.secondlife.secondlife.service.SettlementService;
import com.secondlife.secondlife.common.PageableUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

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

    @Override
    @Transactional
    public OrderResponseDTO createOrder(UUID buyerId, OrderRequestDTO requestDTO) {
        if (requestDTO.getRequestId()==null) throw new BadRequestException("requestId is required for safe order retries");
        Post post = postRepository.findByIdForUpdate(requestDTO.getPostId())
                .orElseThrow(() -> new NotFoundException("Post not found"));

        var replay=orderRepository.findByBuyerIdAndRequestId(buyerId,requestDTO.getRequestId());
        if (replay.isPresent()) {
            var previous=replay.get();
            if (!previous.getPost().getId().equals(requestDTO.getPostId())
                    || !java.util.Objects.equals(previous.getShippingQuoteId(),requestDTO.getShippingQuoteId())
                    || !java.util.Objects.equals(previous.getNegotiation()==null?null:previous.getNegotiation().getId(),requestDTO.getNegotiationId()))
                throw new ConflictException("requestId was already used with different order details");
            return mapToDTO(previous);
        }

        if (!"ACTIVE".equals(post.getStatus())) {
            throw new ConflictException("Post is not available for purchase");
        }

        if (post.getUser().getId().equals(buyerId)) {
            throw new BadRequestException("You cannot buy your own post");
        }

        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new NotFoundException("Buyer not found"));

        BigDecimal finalPrice = post.getPrice();
        Negotiation negotiation = null;

        if (requestDTO.getNegotiationId() != null) {
            negotiation = negotiationRepository.findById(requestDTO.getNegotiationId())
                    .orElseThrow(() -> new NotFoundException("Negotiation not found"));

            if (!negotiation.getBuyer().getId().equals(buyerId) || !negotiation.getPost().getId().equals(post.getId())) {
                throw new BadRequestException("Invalid negotiation for this order");
            }

            if (negotiation.getStatus() != NegotiationStatus.ACCEPTED) {
                throw new BadRequestException("Negotiation is not ACCEPTED");
            }

            if (negotiation.getExpiredAt() != null && negotiation.getExpiredAt().isBefore(Instant.now())) {
                throw new BadRequestException("Negotiation has expired");
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
        order.setRequestId(requestDTO.getRequestId());
        order.setStatus(OrderStatus.PROCESSING); // assuming payment is immediate
        order.setEscrowStatus(EscrowStatus.HELD);
        
        order = orderRepository.saveAndFlush(order);
        commissions.capture(order, buyerId, "Order created");
        var shippingQuote=shippingQuotes.consume(buyerId,post.getId(),requestDTO.getShippingQuoteId(),order.getId(),finalPrice);
        order.setShippingQuoteId(shippingQuote.getId()); order.setShippingFee(shippingQuote.getFee()); order.setDeliveryAddress(shippingQuote.getDeliveryAddress());
        orderRepository.save(order);

        // Deduct money from buyer. This will throw an exception if insufficient balance
        walletService.processPayment(buyerId, finalPrice.add(order.getShippingFee()), order.getId());

        // Update post status so it can't be bought again
        post.setStatus("SOLD");
        postRepository.save(post);

        // Update negotiation status
        if (negotiation != null) {
            negotiation.setStatus(NegotiationStatus.COMPLETED);
            negotiationRepository.save(negotiation);
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

        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new BadRequestException("Order is not in PROCESSING state");
        }

        throw new ConflictException("Shipment status is updated from GHN; create a shipment and wait for carrier pickup");
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
            throw new ConflictException("GHN must confirm delivery before the buyer can release escrow");

        if (order.getEscrowStatus() != EscrowStatus.HELD) {
            throw new ConflictException("Escrow funds are not currently HELD; resolve the shipping issue or return decision first");
        }

        // Persist settlement and release the net seller amount using the order's policy snapshot.
        settlements.settle(order);

        order.setStatus(OrderStatus.COMPLETED);
        order.setEscrowStatus(EscrowStatus.RELEASED);
        return mapToDTO(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponseDTO cancelOrder(UUID userId, UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));

        if (!order.getBuyer().getId().equals(userId) && !order.getSeller().getId().equals(userId)) {
            throw new BadRequestException("You are not authorized to cancel this order");
        }

        if (order.getStatus()==OrderStatus.CANCELLED && order.getEscrowStatus()==EscrowStatus.REFUNDED) return mapToDTO(order);
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Order cannot be cancelled in current status");
        }

        if (order.getEscrowStatus() != EscrowStatus.HELD) {
            throw new BadRequestException("Escrow funds are not HELD. Cannot cancel and refund.");
        }
        var shipping=shipments.findByOrderIdOrderByCreatedAtAsc(orderId);
        if (shipping.stream().anyMatch(s -> !"CANCELLED".equals(s.getStatus())) || order.getStatus()!=OrderStatus.PROCESSING)
            throw new ConflictException("Cancel the carrier shipment before cancelling the order; shipped/delivered goods require return review");

        // Refund buyer
        // Carrier-created shipments may incur pickup/cancellation charges; don't silently refund a consumed shipping fee.
        BigDecimal refund=order.getFinalPrice();
        if (shipping.isEmpty()) refund=refund.add(order.getShippingFee());
        walletService.processRefund(order.getBuyer().getId(), refund, order.getId());

        order.setStatus(OrderStatus.CANCELLED);
        order.setEscrowStatus(EscrowStatus.REFUNDED);

        // Revert post status to ACTIVE
        Post post = postRepository.findByIdForUpdate(order.getPost().getId()).orElseThrow();
        post.setStatus("ACTIVE");
        postRepository.save(post);

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
        OrderResponseDTO dto = new OrderResponseDTO();
        dto.setId(order.getId());
        dto.setPostId(order.getPost().getId());
        dto.setPostTitle(order.getPost().getTitle());
        dto.setBuyerId(order.getBuyer().getId());
        dto.setSellerId(order.getSeller().getId());
        dto.setNegotiationId(order.getNegotiation() != null ? order.getNegotiation().getId() : null);
        dto.setFinalPrice(order.getFinalPrice());
        dto.setShippingQuoteId(order.getShippingQuoteId()); dto.setShippingFee(order.getShippingFee());
        dto.setTotalPaid(order.getFinalPrice().add(order.getShippingFee())); dto.setShippingDeliveredAt(order.getShippingDeliveredAt());
        dto.setStatus(order.getStatus());
        dto.setEscrowStatus(order.getEscrowStatus());
        dto.setCreatedAt(order.getCreatedAt());
        return dto;
    }
}
