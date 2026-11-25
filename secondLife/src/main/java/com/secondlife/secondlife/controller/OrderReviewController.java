package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.CreateOrderReviewRequest;
import com.secondlife.secondlife.dto.request.UpdateOrderReviewRequest;
import com.secondlife.secondlife.dto.response.OrderReviewResponse;
import com.secondlife.secondlife.dto.response.SellerReviewSummaryResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.OrderReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Order Reviews & Ratings", description = "Quản lý đánh giá sản phẩm sau khi giao dịch đơn hàng thành công")
public class OrderReviewController {

    private final OrderReviewService orderReviewService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/orders/{orderId}/reviews")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Người mua gửi đánh giá đơn hàng đã hoàn tất (trong vòng 48h từ lúc giao hàng)")
    public ResponseEntity<ApiResponse<OrderReviewResponse>> createReview(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateOrderReviewRequest request) {
        UUID buyerId = currentUserProvider.resolveUserId(user);
        OrderReviewResponse response = orderReviewService.createReview(buyerId, orderId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Đánh giá sản phẩm thành công", response));
    }

    @PutMapping("/orders/{orderId}/reviews")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Người mua chỉnh sửa đánh giá (cho phép sửa tối đa 1 lần trong 48h)")
    public ApiResponse<OrderReviewResponse> updateReview(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderReviewRequest request) {
        UUID buyerId = currentUserProvider.resolveUserId(user);
        OrderReviewResponse response = orderReviewService.updateReview(buyerId, orderId, request);
        return ApiResponse.success("Chỉnh sửa đánh giá thành công", response);
    }

    @DeleteMapping("/orders/{orderId}/reviews")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Người mua xóa đánh giá (trong vòng 48h)")
    public ApiResponse<Void> deleteReview(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID orderId) {
        UUID buyerId = currentUserProvider.resolveUserId(user);
        orderReviewService.deleteReview(buyerId, orderId);
        return ApiResponse.success("Xóa đánh giá thành công", null);
    }

    @GetMapping("/orders/{orderId}/review")
    @Operation(summary = "Xem đánh giá chi tiết của đơn hàng kèm quyền chỉnh sửa/xóa")
    public ApiResponse<OrderReviewResponse> getOrderReview(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID orderId) {
        UUID currentUserId = user != null ? currentUserProvider.resolveUserId(user) : null;
        OrderReviewResponse response = orderReviewService.getReviewByOrderId(orderId, currentUserId);
        return ApiResponse.success(response);
    }

    @GetMapping("/sellers/{sellerId}/reviews")
    @Operation(summary = "Lấy danh sách đánh giá công khai của người bán")
    public ApiResponse<PageResponse<OrderReviewResponse>> getSellerReviews(
            @PathVariable UUID sellerId,
            @ParameterObject Pageable pageable) {
        return ApiResponse.success(orderReviewService.getSellerReviews(sellerId, pageable));
    }

    @GetMapping("/sellers/{sellerId}/reviews/summary")
    @Operation(summary = "Lấy tóm tắt điểm đánh giá sao và số lượng đánh giá của người bán")
    public ApiResponse<SellerReviewSummaryResponse> getSellerSummary(
            @PathVariable UUID sellerId) {
        return ApiResponse.success(orderReviewService.getSellerSummary(sellerId));
    }

    @GetMapping("/posts/{postId}/reviews")
    @Operation(summary = "Lấy danh sách đánh giá của sản phẩm đăng bán")
    public ApiResponse<PageResponse<OrderReviewResponse>> getPostReviews(
            @PathVariable UUID postId,
            @ParameterObject Pageable pageable) {
        return ApiResponse.success(orderReviewService.getPostReviews(postId, pageable));
    }
}
