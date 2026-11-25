package com.secondlife.secondlife.service;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.CreateOrderReviewRequest;
import com.secondlife.secondlife.dto.request.UpdateOrderReviewRequest;
import com.secondlife.secondlife.dto.response.OrderReviewResponse;
import com.secondlife.secondlife.dto.response.SellerReviewSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderReviewService {

    OrderReviewResponse createReview(UUID buyerId, UUID orderId, CreateOrderReviewRequest request);

    OrderReviewResponse updateReview(UUID buyerId, UUID orderId, UpdateOrderReviewRequest request);

    void deleteReview(UUID buyerId, UUID orderId);

    OrderReviewResponse getReviewByOrderId(UUID orderId, UUID currentUserId);

    PageResponse<OrderReviewResponse> getSellerReviews(UUID sellerId, Pageable pageable);

    SellerReviewSummaryResponse getSellerSummary(UUID sellerId);

    PageResponse<OrderReviewResponse> getPostReviews(UUID postId, Pageable pageable);
}
