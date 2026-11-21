package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.CommissionRuleResponse;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.dto.response.OrderBankPaymentResponse;
import com.secondlife.secondlife.dto.response.OrderFeedItemResponse;
import com.secondlife.secondlife.dto.response.OrderFeedResponse;
import com.secondlife.secondlife.dto.response.OrderResponseDTO;
import com.secondlife.secondlife.dto.response.SellerCommissionPreviewResponse;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.enums.InspectionScope;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.mapper.InspectionOrderMapper;
import com.secondlife.secondlife.mapper.OrderMapper;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.OrderRepository.FeedCounts;
import com.secondlife.secondlife.repository.OrderRepository.FeedRow;
import com.secondlife.secondlife.service.CommissionCalculator;
import com.secondlife.secondlife.service.CommissionService;
import com.secondlife.secondlife.service.OrderBankPaymentService;
import com.secondlife.secondlife.service.OrderFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderFeedServiceImpl implements OrderFeedService {
    private static final Set<String> FILTERS = Set.of("all", "awaiting_pickup", "inspecting", "shipping",
            "delivered", "completed", "cancelled", "disputed", "with_inspection", "pre_listing");
    private static final Set<String> PERSPECTIVES = Set.of("all", "buyer", "seller");
    private static final Set<String> INSPECTION_PREVIEW_STATUSES = Set.of(
            "PENDING", "WAITING_SELLER_APPROVAL", "WAITING_BUYER_APPROVAL", "WAITING_FEE_PAYMENT",
            "CONFIRMED_PENDING_DISPATCH", "DISPATCHED", "SCHEDULED", "SHIPPING_TO_CENTER", "INSPECTING", "PASSED");

    private final OrderRepository orderRepository;
    private final InspectionOrderRepository inspectionOrderRepository;
    private final OrderBankPaymentService bankPayments;
    private final OrderMapper orderMapper;
    private final InspectionOrderMapper inspectionMapper;
    private final CommissionService commissions;
    private final CommissionCalculator commissionCalculator;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrderFeedResponse read(UUID actor, String perspective, String filter, String query, int page, int size,
                                  UUID detail) {
        if (!PERSPECTIVES.contains(perspective) || !FILTERS.contains(filter)
                || page < 0 || size < 1 || size > 50 || query.length() > 150) {
            throw new BadRequestException("Bộ lọc đơn hàng không hợp lệ.");
        }

        String search = query.strip().replaceFirst("^#", "").toLowerCase(Locale.ROOT);
        Map<String, Long> counts = toCounts(orderRepository.countFeedRows(actor, perspective, search));
        long total = counts.get(filter);
        int totalPages = (int) Math.min(Integer.MAX_VALUE, (total + size - 1) / size);
        int currentPage = Math.min(page, Math.max(0, totalPages - 1));

        List<FeedRow> rows = orderRepository.findFeedRows(
                actor, perspective, search, filter, size, (long) currentPage * size, detail);
        Map<UUID, OrderResponseDTO> orders = loadOrders(rows);
        Map<UUID, InspectionOrderResponse> inspections = loadInspections(rows);
        CommissionRuleResponse previewRule = inspections.values().stream()
                .anyMatch(inspection -> canPreviewCommission(actor, inspection))
                ? commissions.findActiveDefaultRule().orElse(null)
                : null;

        List<OrderFeedItemResponse> items = new ArrayList<>();
        OrderFeedItemResponse detailItem = null;
        for (FeedRow row : rows) {
            UUID inspectionId = row.getInspectionId();
            InspectionOrderResponse inspection = inspectionId == null ? null : inspections.get(inspectionId);
            OrderResponseDTO order = orders.get(row.getId());
            SellerCommissionPreviewResponse preview = order == null && previewRule != null
                    && canPreviewCommission(actor, inspection) ? previewCommission(inspection, previewRule) : null;
            OrderFeedItemResponse item = orderMapper.toFeedItem(row, order, inspection, preview);
            if (row.isDetail()) {
                detailItem = item;
            } else {
                items.add(item);
            }
        }
        return new OrderFeedResponse(items, detailItem, counts, currentPage, size, total, totalPages);
    }

    private boolean canPreviewCommission(UUID actor, InspectionOrderResponse inspection) {
        return inspection != null && actor.equals(inspection.sellerId()) && inspection.linkedOrderId() == null
                && InspectionScope.BUYER_REQUEST.name().equals(inspection.inspectionScope())
                && inspection.status() != null && INSPECTION_PREVIEW_STATUSES.contains(inspection.status());
    }

    private SellerCommissionPreviewResponse previewCommission(InspectionOrderResponse inspection, CommissionRuleResponse rule) {
        BigDecimal price = inspection.finalItemPrice() != null ? inspection.finalItemPrice() : inspection.postPrice();
        if (price == null || price.signum() <= 0) return null;
        var amounts = commissionCalculator.calculate(price, rule.rate(), rule.minCommission(), rule.maxCommission());
        return new SellerCommissionPreviewResponse(rule.rate(), amounts.platformCommission(), amounts.sellerPayout());
    }

    private Map<UUID, OrderResponseDTO> loadOrders(List<FeedRow> rows) {
        List<UUID> orderIds = rows.stream()
                .filter(row -> !row.getId().equals(row.getInspectionId()))
                .map(FeedRow::getId)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        List<Order> orders = orderRepository.findFeedOrdersByIds(orderIds);
        Map<UUID, OrderBankPaymentResponse> payments = bankPayments.detailsForOrders(orders);
        return orders.stream().collect(Collectors.toMap(Order::getId, order ->
                orderMapper.toResponse(order, payments.get(order.getId()), order.getFinalPrice().add(order.getShippingFee()))));
    }

    private Map<UUID, InspectionOrderResponse> loadInspections(List<FeedRow> rows) {
        List<UUID> inspectionIds = rows.stream()
                .map(FeedRow::getInspectionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (inspectionIds.isEmpty()) {
            return Map.of();
        }
        return inspectionOrderRepository.findFeedInspectionsByIds(inspectionIds).stream()
                .map(inspectionMapper::toResponse)
                .collect(Collectors.toMap(InspectionOrderResponse::id, Function.identity()));
    }

    private Map<String, Long> toCounts(FeedCounts result) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", result.getAllCount());
        counts.put("awaiting_pickup", result.getAwaitingPickup());
        counts.put("inspecting", result.getInspecting());
        counts.put("shipping", result.getShipping());
        counts.put("delivered", result.getDelivered());
        counts.put("completed", result.getCompleted());
        counts.put("cancelled", result.getCancelled());
        counts.put("disputed", result.getDisputed());
        counts.put("with_inspection", result.getWithInspection());
        counts.put("pre_listing", result.getPreListing());
        return counts;
    }
}
