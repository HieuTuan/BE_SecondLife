package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.AdminOrderResponse;
import com.secondlife.secondlife.dto.response.AdminOrderStatsResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.OrderStatus;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.service.AdminOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminOrderServiceImpl implements AdminOrderService {

    private final OrderRepository orderRepository;
    private final InspectionOrderRepository inspectionOrderRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminOrderResponse> getAdminOrders(
            OrderStatus status,
            EscrowStatus escrowStatus,
            String search,
            Boolean hasInspection,
            Pageable pageable) {

        UUID searchOrderId = null;
        String searchKeyword = null;
        if (search != null && !search.isBlank()) {
            String trimmed = search.trim();
            try {
                searchOrderId = UUID.fromString(trimmed.startsWith("#") ? trimmed.substring(1) : trimmed);
            } catch (IllegalArgumentException ignored) {
                searchKeyword = trimmed;
            }
        }

        Page<Order> orderPage;
        try {
            orderPage = orderRepository.findAdminOrders(status, escrowStatus, searchOrderId, searchKeyword, pageable);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new com.secondlife.secondlife.exception.BadRequestException("DEBUG: " + ex.getClass().getName() + " - " + ex.getMessage());
        }

        List<Order> orders = orderPage.getContent();
        if (orders.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, orderPage.getTotalElements());
        }

        List<AdminOrderResponse> responseList = orders.stream()
                .map(o -> {
                    InspectionOrder inspection = null;
                    try {
                        inspection = inspectionOrderRepository.findByOrderId(o.getId()).orElse(null);
                    } catch (Exception ignored) { }
                    return mapToAdminOrderResponse(o, inspection);
                })
                .filter(res -> {
                    if (hasInspection == null) return true;
                    return hasInspection ? res.hasInspection() : !res.hasInspection();
                })
                .toList();

        return new PageImpl<>(responseList, pageable, orderPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOrderResponse getAdminOrderById(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng với mã " + id));

        Optional<InspectionOrder> linkedInspection = Optional.empty();
        try {
            linkedInspection = inspectionOrderRepository.findByOrderId(order.getId());
        } catch (Exception ignored) { }

        return mapToAdminOrderResponse(order, linkedInspection.orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOrderStatsResponse getAdminOrderStats() {
        var projection = orderRepository.getAdminOrderStats();
        long withInspectionCount = 0;
        try {
            withInspectionCount = inspectionOrderRepository.count();
        } catch (Exception ignored) { }

        return AdminOrderStatsResponse.builder()
                .totalOrders(projection != null ? projection.getTotalOrders() : 0)
                .confirmedOrders(projection != null ? projection.getConfirmedOrders() : 0)
                .processingOrders(projection != null ? projection.getProcessingOrders() : 0)
                .shippingOrders(projection != null ? projection.getShippingOrders() : 0)
                .deliveredOrders(projection != null ? projection.getDeliveredOrders() : 0)
                .completedOrders(projection != null ? projection.getCompletedOrders() : 0)
                .cancelledOrders(projection != null ? projection.getCancelledOrders() : 0)
                .disputedOrders(projection != null ? projection.getDisputedOrders() : 0)
                .totalGmv(projection != null && projection.getTotalGmv() != null ? projection.getTotalGmv() : BigDecimal.ZERO)
                .heldEscrowAmount(projection != null && projection.getHeldEscrowAmount() != null ? projection.getHeldEscrowAmount() : BigDecimal.ZERO)
                .withInspectionCount(withInspectionCount)
                .build();
    }

    private AdminOrderResponse mapToAdminOrderResponse(Order order, InspectionOrder inspection) {
        Post post = order.getPost();
        User buyer = order.getBuyer();
        User seller = order.getSeller();

        String postImg = null;
        if (post != null) {
            List<String> urls = post.getImageUrls();
            if (urls != null && !urls.isEmpty()) {
                postImg = urls.get(0);
            } else {
                postImg = post.getImageUrl();
            }
        }

        String buyerName = (buyer != null && buyer.getEmail() != null) ? buyer.getEmail() : "Người mua";
        String buyerPhone = null;
        if (buyer != null && buyer.getProfile() != null) {
            if (buyer.getProfile().getFullName() != null && !buyer.getProfile().getFullName().isBlank()) {
                buyerName = buyer.getProfile().getFullName();
            }
            buyerPhone = buyer.getProfile().getPhone();
        }

        String sellerName = (seller != null && seller.getEmail() != null) ? seller.getEmail() : "Người bán";
        String sellerPhone = null;
        if (seller != null && seller.getProfile() != null) {
            if (seller.getProfile().getFullName() != null && !seller.getProfile().getFullName().isBlank()) {
                sellerName = seller.getProfile().getFullName();
            }
            sellerPhone = seller.getProfile().getPhone();
        }

        BigDecimal shippingFee = order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO;
        BigDecimal finalPrice = order.getFinalPrice() != null ? order.getFinalPrice() : BigDecimal.ZERO;
        BigDecimal totalAmount = finalPrice.add(shippingFee);

        return AdminOrderResponse.builder()
                .id(order.getId())
                .postId(post != null ? post.getId() : null)
                .postTitle(post != null ? post.getTitle() : "Sản phẩm")
                .postImageUrl(postImg)
                .postPrice(post != null ? post.getPrice() : finalPrice)
                .buyerId(buyer != null ? buyer.getId() : null)
                .buyerName(buyerName)
                .buyerEmail(buyer != null ? buyer.getEmail() : null)
                .buyerPhone(buyerPhone)
                .sellerId(seller != null ? seller.getId() : null)
                .sellerName(sellerName)
                .sellerEmail(seller != null ? seller.getEmail() : null)
                .sellerPhone(sellerPhone)
                .finalPrice(finalPrice)
                .shippingFee(shippingFee)
                .totalAmount(totalAmount)
                .status(order.getStatus())
                .escrowStatus(order.getEscrowStatus())
                .paymentMethod(order.getPaymentMethod())
                .deliveryAddress(order.getDeliveryAddress())
                .cancellationReasonCode(order.getCancellationReasonCode())
                .hasInspection(inspection != null)
                .inspectionOrderId(inspection != null ? inspection.getId() : null)
                .inspectionStatus(inspection != null && inspection.getStatus() != null ? inspection.getStatus() : null)
                .createdAt(order.getCreatedAt())
                .paidAt(order.getPaidAt())
                .sellerConfirmedAt(order.getSellerConfirmedAt())
                .shippingDeliveredAt(order.getShippingDeliveredAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
