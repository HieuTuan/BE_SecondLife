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
import com.secondlife.secondlife.repository.NegotiationRepository;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.OrderService;
import com.secondlife.secondlife.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NegotiationRepository negotiationRepository;
    private final WalletService walletService;

    @Override
    @Transactional
    public OrderResponseDTO createOrder(UUID buyerId, OrderRequestDTO requestDTO) {
        Post post = postRepository.findById(requestDTO.getPostId())
                .orElseThrow(() -> new NotFoundException("Post not found"));

        if (!"ACTIVE".equals(post.getStatus())) {
            throw new BadRequestException("Post is not available for purchase");
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
        order.setStatus(OrderStatus.PROCESSING); // assuming payment is immediate
        order.setEscrowStatus(EscrowStatus.HELD);
        
        order = orderRepository.save(order);

        // Deduct money from buyer. This will throw an exception if insufficient balance
        walletService.processPayment(buyerId, finalPrice, order.getId());

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
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));

        if (!order.getSeller().getId().equals(sellerId)) {
            throw new BadRequestException("Only the seller can mark the order as shipped");
        }

        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new BadRequestException("Order is not in PROCESSING state");
        }

        order.setStatus(OrderStatus.SHIPPED);
        return mapToDTO(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponseDTO confirmDelivery(UUID buyerId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));

        if (!order.getBuyer().getId().equals(buyerId)) {
            throw new BadRequestException("Only the buyer can confirm delivery");
        }

        if (order.getStatus() != OrderStatus.SHIPPED && order.getStatus() != OrderStatus.PROCESSING) {
            throw new BadRequestException("Cannot confirm delivery in current status");
        }

        if (order.getEscrowStatus() != EscrowStatus.HELD) {
            throw new BadRequestException("Escrow funds are not currently HELD");
        }

        // Release funds to seller
        walletService.processEarning(order.getSeller().getId(), order.getFinalPrice(), order.getId());

        order.setStatus(OrderStatus.COMPLETED);
        order.setEscrowStatus(EscrowStatus.RELEASED);
        return mapToDTO(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponseDTO cancelOrder(UUID userId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));

        if (!order.getBuyer().getId().equals(userId) && !order.getSeller().getId().equals(userId)) {
            throw new BadRequestException("You are not authorized to cancel this order");
        }

        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Order cannot be cancelled in current status");
        }

        if (order.getEscrowStatus() != EscrowStatus.HELD) {
            throw new BadRequestException("Escrow funds are not HELD. Cannot cancel and refund.");
        }

        // Refund buyer
        walletService.processRefund(order.getBuyer().getId(), order.getFinalPrice(), order.getId());

        order.setStatus(OrderStatus.CANCELLED);
        order.setEscrowStatus(EscrowStatus.REFUNDED);

        // Revert post status to ACTIVE
        Post post = order.getPost();
        post.setStatus("ACTIVE");
        postRepository.save(post);

        return mapToDTO(orderRepository.save(order));
    }

    @Override
    public Page<OrderResponseDTO> getBuyerOrders(UUID buyerId, Pageable pageable) {
        return orderRepository.findByBuyerId(buyerId, pageable).map(this::mapToDTO);
    }

    @Override
    public Page<OrderResponseDTO> getSellerOrders(UUID sellerId, Pageable pageable) {
        return orderRepository.findBySellerId(sellerId, pageable).map(this::mapToDTO);
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
        dto.setStatus(order.getStatus());
        dto.setEscrowStatus(order.getEscrowStatus());
        dto.setCreatedAt(order.getCreatedAt());
        return dto;
    }
}
