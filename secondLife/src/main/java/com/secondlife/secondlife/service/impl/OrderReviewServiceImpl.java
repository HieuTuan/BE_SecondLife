package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.CreateOrderReviewRequest;
import com.secondlife.secondlife.dto.request.UpdateOrderReviewRequest;
import com.secondlife.secondlife.dto.response.OrderReviewResponse;
import com.secondlife.secondlife.dto.response.SellerReviewSummaryResponse;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.entity.OrderReview;
import com.secondlife.secondlife.enums.OrderStatus;
import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.OrderReviewRepository;
import com.secondlife.secondlife.repository.UserReportRepository;
import com.secondlife.secondlife.service.OrderReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderReviewServiceImpl implements OrderReviewService {

    private final OrderReviewRepository orderReviewRepository;
    private final OrderRepository orderRepository;
    private final UserReportRepository userReportRepository;

    private static final int MAX_EDITS = 1;
    private static final Duration REVIEW_WINDOW = Duration.ofHours(48);

    @Override
    @Transactional
    public OrderReviewResponse createReview(UUID buyerId, UUID orderId, CreateOrderReviewRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng với mã: " + orderId));

        if (order.getBuyer() == null || !order.getBuyer().getId().equals(buyerId)) {
            throw new ForbiddenException("Chỉ người mua của đơn hàng mới có quyền đánh giá sản phẩm.");
        }

        if (order.getSeller() != null && order.getSeller().getId().equals(buyerId)) {
            throw new ForbiddenException("Người bán không thể tự đánh giá đơn hàng của chính mình.");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Đơn hàng đã bị hủy, không thể đánh giá.");
        }

        if (order.getStatus() != OrderStatus.DELIVERED && order.getStatus() != OrderStatus.COMPLETED) {
            throw new BadRequestException("Đơn hàng chưa giao thành công. Chỉ được đánh giá khi đơn hàng đã giao (DELIVERED) hoặc hoàn tất (COMPLETED).");
        }

        if (order.isShippingHold() || order.getEscrowStatus() == EscrowStatus.FROZEN || userReportRepository.hasOrderHold(orderId)) {
            throw new ConflictException("Đơn hàng đang có khiếu nại tranh chấp hoặc tiền ký quỹ đang bị đóng băng. Không thể gửi đánh giá.");
        }

        if (orderReviewRepository.existsByOrderId(orderId)) {
            throw new ConflictException("Đơn hàng này đã được đánh giá rồi.");
        }

        Instant deliveredAt = order.getShippingDeliveredAt() != null ? order.getShippingDeliveredAt() : order.getUpdatedAt();
        Instant deadline = deliveredAt.plus(REVIEW_WINDOW);
        if (Instant.now().isAfter(deadline)) {
            throw new ConflictException("Đã quá thời hạn 48 giờ kể từ khi giao hàng. Không thể gửi đánh giá.");
        }

        OrderReview review = OrderReview.builder()
                .order(order)
                .buyer(order.getBuyer())
                .seller(order.getSeller())
                .post(order.getPost())
                .rating(request.getRating())
                .comment(request.getComment() != null ? request.getComment().trim() : "")
                .tags(request.getTags() != null ? request.getTags() : List.of())
                .photos(request.getPhotos() != null ? request.getPhotos() : List.of())
                .editCount(0)
                .verifiedPurchase(true)
                .conditionGrade(order.getPost() != null ? order.getPost().getItemCondition() : null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        review = orderReviewRepository.save(review);
        log.info("Created review {} for order {} by buyer {}", review.getId(), orderId, buyerId);
        return mapToResponse(review, buyerId);
    }

    @Override
    @Transactional
    public OrderReviewResponse updateReview(UUID buyerId, UUID orderId, UpdateOrderReviewRequest request) {
        OrderReview review = orderReviewRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đánh giá cho đơn hàng: " + orderId));

        if (!review.getBuyer().getId().equals(buyerId)) {
            throw new ForbiddenException("Bạn không có quyền chỉnh sửa đánh giá này.");
        }

        Order order = review.getOrder();
        Instant deliveredAt = order != null && order.getShippingDeliveredAt() != null ? order.getShippingDeliveredAt() : (order != null ? order.getUpdatedAt() : review.getCreatedAt());
        Instant deadline = deliveredAt.plus(REVIEW_WINDOW);
        if (Instant.now().isAfter(deadline)) {
            throw new ConflictException("Đã quá thời hạn 48 giờ kể từ khi nhận hàng. Đánh giá đã được đóng băng vĩnh viễn và không thể chỉnh sửa.");
        }

        if (review.getEditCount() >= MAX_EDITS) {
            throw new ConflictException("Bạn chỉ được phép chỉnh sửa đánh giá tối đa 1 lần trong vòng 48 giờ. Đánh giá này đã từng được chỉnh sửa.");
        }

        if (request.getRating() != null) {
            review.setRating(request.getRating());
        }
        if (request.getComment() != null && !request.getComment().trim().isEmpty()) {
            review.setComment(request.getComment().trim());
        }
        if (request.getTags() != null) {
            review.setTags(request.getTags());
        }
        if (request.getPhotos() != null) {
            review.setPhotos(request.getPhotos());
        }
        review.setEditCount(review.getEditCount() + 1);
        review.setUpdatedAt(Instant.now());

        review = orderReviewRepository.save(review);
        log.info("Updated review {} for order {} by buyer {} (editCount: {})", review.getId(), orderId, buyerId, review.getEditCount());
        return mapToResponse(review, buyerId);
    }

    @Override
    @Transactional
    public void deleteReview(UUID buyerId, UUID orderId) {
        OrderReview review = orderReviewRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đánh giá cho đơn hàng: " + orderId));

        if (!review.getBuyer().getId().equals(buyerId)) {
            throw new ForbiddenException("Bạn không có quyền xóa đánh giá này.");
        }

        Order order = review.getOrder();
        Instant deliveredAt = order != null && order.getShippingDeliveredAt() != null ? order.getShippingDeliveredAt() : (order != null ? order.getUpdatedAt() : review.getCreatedAt());
        Instant deadline = deliveredAt.plus(REVIEW_WINDOW);
        if (Instant.now().isAfter(deadline)) {
            throw new ConflictException("Đã quá thời hạn 48 giờ kể từ khi nhận hàng. Đánh giá đã được đóng băng vĩnh viễn và không thể xóa.");
        }

        orderReviewRepository.delete(review);
        log.info("Deleted review {} for order {} by buyer {}", review.getId(), orderId, buyerId);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderReviewResponse getReviewByOrderId(UUID orderId, UUID currentUserId) {
        OrderReview review = orderReviewRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Chưa có đánh giá cho đơn hàng này."));
        return mapToResponse(review, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderReviewResponse> getSellerReviews(UUID sellerId, Pageable pageable) {
        Page<OrderReview> page = orderReviewRepository.findBySellerIdOrderByCreatedAtDesc(sellerId, pageable);
        return PageResponse.from(page.map(r -> mapToResponse(r, null)));
    }

    @Override
    @Transactional(readOnly = true)
    public SellerReviewSummaryResponse getSellerSummary(UUID sellerId) {
        Double avg = orderReviewRepository.getAverageRatingBySellerId(sellerId);
        long total = orderReviewRepository.countBySellerId(sellerId);

        Map<Integer, Long> ratingCounts = new HashMap<>();
        for (int star = 1; star <= 5; star++) {
            ratingCounts.put(star, 0L);
        }

        List<Object[]> rows = orderReviewRepository.countRatingsGroupedBySellerId(sellerId);
        if (rows != null) {
            for (Object[] row : rows) {
                Integer rating = (Integer) row[0];
                Long count = (Long) row[1];
                if (rating != null) {
                    ratingCounts.put(rating, count);
                }
            }
        }

        return SellerReviewSummaryResponse.builder()
                .sellerId(sellerId)
                .averageRating(avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0)
                .totalReviews(total)
                .ratingCounts(ratingCounts)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderReviewResponse> getPostReviews(UUID postId, Pageable pageable) {
        Page<OrderReview> page = orderReviewRepository.findByPostIdOrderByCreatedAtDesc(postId, pageable);
        return PageResponse.from(page.map(r -> mapToResponse(r, null)));
    }

    private OrderReviewResponse mapToResponse(OrderReview review, UUID currentUserId) {
        boolean isBuyer = currentUserId != null && review.getBuyer() != null && review.getBuyer().getId().equals(currentUserId);
        Order order = review.getOrder();
        Instant deliveredAt = order != null && order.getShippingDeliveredAt() != null
                ? order.getShippingDeliveredAt()
                : (order != null ? order.getUpdatedAt() : review.getCreatedAt());
        Instant deadline = deliveredAt.plus(REVIEW_WINDOW);
        Instant now = Instant.now();
        boolean withinDeadline = now.isBefore(deadline);

        boolean canEdit = isBuyer && review.getEditCount() < MAX_EDITS && withinDeadline;
        boolean canDelete = isBuyer && withinDeadline;
        long remainingEditSeconds = Math.max(0, Duration.between(now, deadline).toSeconds());

        String buyerName = "Người mua";
        String buyerAvatar = null;
        if (review.getBuyer() != null && review.getBuyer().getProfile() != null) {
            if (review.getBuyer().getProfile().getFullName() != null && !review.getBuyer().getProfile().getFullName().isBlank()) {
                buyerName = review.getBuyer().getProfile().getFullName();
            }
            buyerAvatar = review.getBuyer().getProfile().getAvatarUrl();
        }

        String sellerName = "Người bán";
        if (review.getSeller() != null && review.getSeller().getProfile() != null && review.getSeller().getProfile().getFullName() != null) {
            sellerName = review.getSeller().getProfile().getFullName();
        }

        String productTitle = null;
        String productImage = null;
        if (review.getPost() != null) {
            productTitle = review.getPost().getTitle();
            List<String> urls = review.getPost().getImageUrls();
            if (urls != null && !urls.isEmpty()) {
                productImage = urls.get(0);
            }
        }

        return OrderReviewResponse.builder()
                .id(review.getId())
                .orderId(review.getOrder() != null ? review.getOrder().getId() : null)
                .buyerId(review.getBuyer() != null ? review.getBuyer().getId() : null)
                .buyerName(buyerName)
                .buyerAvatar(buyerAvatar)
                .sellerId(review.getSeller() != null ? review.getSeller().getId() : null)
                .sellerName(sellerName)
                .postId(review.getPost() != null ? review.getPost().getId() : null)
                .productTitle(productTitle)
                .productImage(productImage)
                .conditionGrade(review.getConditionGrade())
                .rating(review.getRating())
                .comment(review.getComment())
                .tags(review.getTags())
                .photos(review.getPhotos())
                .verifiedPurchase(review.isVerifiedPurchase())
                .editCount(review.getEditCount())
                .maxEdits(MAX_EDITS)
                .canEdit(canEdit)
                .canDelete(canDelete)
                .remainingEditSeconds(remainingEditSeconds)
                .deadline(deadline)
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
