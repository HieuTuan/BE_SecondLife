package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.*;
import com.secondlife.secondlife.dto.response.*;
import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.mapper.InspectionOrderMapper;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PreListingInspectionServiceImpl implements PreListingInspectionService {
    private static final Duration QUOTE_VALIDITY = Duration.ofMinutes(15);
    private static final Duration PAYMENT_WINDOW = Duration.ofHours(12);
    private final PostRepository posts;
    private final InspectionOrderRepository orders;
    private final PreListingInspectionQuoteRepository quotes;
    private final InspectionPricingService pricing;
    private final InspectionLocationService locations;
    private final ListingAccessService access;
    private final InspectionOrderMapper mapper;
    private final ObjectMapper json;

    @Override
    @Transactional
    public PreListingInspectionQuoteResponse quote(UUID sellerId, UUID postId, PreListingInspectionQuoteRequest request) {
        Post post = ownedPost(sellerId, postId);
        requirePendingPost(post);
        orders.findActiveByPostId(postId).ifPresent(this::requireEditable);
        // The pre-listing workflow always uses the Hub selected by pricing.
        InspectionQuoteResponse quote = pricing.quote(InspectionQuoteRequest.builder()
                .postId(postId).orderType(request.orderType()).build());
        Instant expiresAt = Instant.now().plus(QUOTE_VALIDITY);
        if (quote.shippingQuote() != null && quote.shippingQuote().expiresAt().isBefore(expiresAt)) {
            expiresAt = quote.shippingQuote().expiresAt();
        }
        var saved = new PreListingInspectionQuote();
        saved.setPostId(postId);
        saved.setOrderType(request.orderType());
        saved.setExpiresAt(expiresAt);
        saved.setQuoteJson(json.writeValueAsString(quote));
        saved.setSellerPickupJson(json.writeValueAsString(locations.sellerPickup(post)));
        quotes.saveAndFlush(saved);
        return new PreListingInspectionQuoteResponse(saved.getId(), expiresAt, request.orderType(), quote);
    }

    @Override
    @Transactional
    public InspectionOrderResponse confirm(UUID sellerId, UUID postId, PreListingInspectionConfirmRequest request) {
        Post post = ownedPost(sellerId, postId);
        var existing = orders.findByPreListingQuoteId(request.quoteId());
        if (existing.isPresent() && postId.equals(existing.get().getPost().getId())) {
            return mapper.toResponse(existing.get());
        }
        requirePendingPost(post);
        Optional<InspectionOrder> active = orders.findActivePreListingIdByPostId(postId)
                .map(id -> orders.findByIdForUpdate(id).orElseThrow());
        active.ifPresent(this::requireEditable);
        var saved = quotes.findById(request.quoteId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy báo giá kiểm định"));
        if (!postId.equals(saved.getPostId())) throw new ForbiddenException("Báo giá không thuộc bài đăng này");
        if (!saved.getExpiresAt().isAfter(Instant.now())) {
            throw new ConflictException("Báo giá kiểm định đã hết hạn; vui lòng lấy báo giá mới");
        }
        if (!saved.getSellerPickupJson().equals(json.writeValueAsString(locations.sellerPickup(post)))) {
            throw new ConflictException("Địa chỉ người bán đã thay đổi; vui lòng lấy báo giá mới");
        }
        var quoted = json.readValue(saved.getQuoteJson(), InspectionQuoteResponse.class);
        var config = quoted.inspectionConfiguration();
        var current = pricing.quote(InspectionQuoteRequest.builder().postId(postId).orderType(saved.getOrderType())
                .inspectionCenterId(quoted.inspectionCenterId()).expectedFeeProposalId(config.feeProposalId())
                .expectedChecklistTemplateId(config.checklistTemplateId())
                .inspectionShippingQuoteId(quoted.shippingQuote() == null ? null : quoted.shippingQuote().id()).build());
        BigDecimal total = total(quoted, saved.getOrderType());
        if (total.compareTo(total(current, saved.getOrderType())) != 0
                || quoted.postPrice().compareTo(post.getPrice()) != 0) {
            throw new ConflictException("Giá bài hoặc phí kiểm định đã thay đổi; vui lòng lấy báo giá mới");
        }
        InspectionOrder order = active.orElseGet(InspectionOrder::new);
        order.setPost(post);
        order.setSeller(post.getUser());
        order.setBuyer(null);
        order.setInspectionScope(InspectionScope.PRE_LISTING);
        order.setOrderType(saved.getOrderType());
        order.setFeePayer(InspectionFeePayer.SELLER_PAYS);
        order.setBuyerFeePercent(0);
        order.setPreviousBuyerFeePercent(null);
        order.setBuyerFeeShare(BigDecimal.ZERO);
        order.setBuyerCommitmentDeposit(BigDecimal.ZERO);
        order.setSellerFeeShare(total);
        order.setFinalItemPrice(post.getPrice());
        order.setSellerConsentZin(request.sellerConsentZin());
        order.setInspectionFee(quoted.baseInspectionFee());
        boolean atHome = saved.getOrderType() == InspectionOrderType.AT_HOME;
        order.setShippingFee(atHome ? BigDecimal.ZERO : quoted.shippingToCenterFee());
        order.setReturnShippingReserveFee(atHome ? BigDecimal.ZERO : quoted.returnShippingReserveFee());
        order.setTravelFee(atHome ? quoted.travelFeeRoundTrip() : BigDecimal.ZERO);
        order.setTravelDistanceKm(atHome ? quoted.travelDistanceKm() : BigDecimal.ZERO);
        order.setCorporateShipping(!atHome);
        order.setCashShippingSimulation(false);
        order.setChecklistSnapshotJson(null);
        pricing.applyConfiguration(order, quoted);
        order.setPreListingQuoteId(saved.getId());
        order.setStatus("WAITING_FEE_PAYMENT");
        order.setPaymentDeadline(Instant.now().plus(PAYMENT_WINDOW));
        return mapper.toResponse(orders.saveAndFlush(order));
    }

    private Post ownedPost(UUID sellerId, UUID postId) {
        Post post = posts.findByIdForUpdate(postId).orElseThrow(() -> new NotFoundException("Không tìm thấy bài đăng"));
        if (!sellerId.equals(post.getUser().getId())) throw new ForbiddenException("Bài đăng không thuộc người bán này");
        access.requireVerifiedSeller(sellerId);
        return post;
    }

    private void requirePendingPost(Post post) {
        if (!"PENDING_INSPECTION".equals(post.getStatus()) || post.isListingCreditCharged()) {
            throw new ConflictException("Bài đăng không ở trạng thái chờ kiểm định trước xuất bản");
        }
        if (orders.existsByPostIdAndInspectionScopeAndStatusIn(post.getId(), InspectionScope.PRE_LISTING,
                List.of("PASSED", "COMPLETED"))) {
            throw new ConflictException("Bài đăng đã đạt kiểm định và đang chờ hoàn tất quy trình đăng bán");
        }
    }

    private void requireEditable(InspectionOrder order) {
        if (order.getId() == null) return;
        if (order.getInspectionScope() != InspectionScope.PRE_LISTING
                || !Set.of("PENDING", "WAITING_FEE_PAYMENT").contains(order.getStatus())
                || order.getSellerPaidAt() != null || order.getBuyerPaidAt() != null
                || order.getInspector() != null || Boolean.TRUE.equals(order.getAssignmentLocked())) {
            throw new ConflictException("Phương án kiểm định đã được thanh toán hoặc tiếp nhận; không thể thay đổi");
        }
    }

    private BigDecimal total(InspectionQuoteResponse quote, InspectionOrderType type) {
        return type == InspectionOrderType.AT_HOME ? quote.atHomeTotalFee() : quote.atCenterTotalFee();
    }
}
