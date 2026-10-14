package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.InspectionBuyRequest;
import com.secondlife.secondlife.dto.request.InspectionQuoteRequest;
import com.secondlife.secondlife.dto.request.InspectionSellerRespondRequest;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.dto.response.InspectionQuoteResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.InspectionEscrowService;
import com.secondlife.secondlife.service.InspectionPricingService;
import com.secondlife.secondlife.service.InspectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inspection")
@RequiredArgsConstructor
@Tag(name = "Product Inspection - Buyer & Seller Flow", description = "APIs dành cho Người mua và Người bán trong quy trình kiểm định sản phẩm")
public class InspectionController {

    private final InspectionService inspectionService;
    private final InspectionPricingService pricingService;
    private final InspectionEscrowService escrowService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/quote")
    @Operation(summary = "Tính toán báo giá chi tiết kiểm định cho bài đăng (cả 2 gói Tại nhà và Tại trung tâm)")
    public ResponseEntity<InspectionQuoteResponse> getQuote(@Valid @RequestBody InspectionQuoteRequest request) {
        return ResponseEntity.ok(pricingService.quote(request));
    }

    @PostMapping("/requests")
    @Operation(summary = "Người mua tạo yêu cầu mua hàng kèm kiểm định sản phẩm")
    public ResponseEntity<InspectionOrderResponse> requestInspection(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody InspectionBuyRequest request) {
        UUID buyerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(inspectionService.requestInspection(buyerId, request));
    }

    @PostMapping("/requests/{orderId}/respond")
    @Operation(summary = "Người bán phản hồi yêu cầu kiểm định (Đồng ý, Từ chối hoặc Đề xuất lại phương án chia phí)")
    public ResponseEntity<InspectionOrderResponse> respondInspection(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID orderId,
            @Valid @RequestBody InspectionSellerRespondRequest request) {
        UUID sellerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(inspectionService.respondInspection(sellerId, orderId, request));
    }

    @PostMapping("/orders/{orderId}/pay-fee")
    @Operation(summary = "Người mua hoặc Người bán nộp phần phí kiểm định ký quỹ từ ví SecondLife (trong vòng 6h)")
    public ResponseEntity<InspectionOrderResponse> payInspectionFee(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID orderId) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        var order = escrowService.payInspectionFee(userId, orderId);
        return ResponseEntity.ok(InspectionOrderResponse.from(order));
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Xem chi tiết đơn kiểm định theo ID")
    public ResponseEntity<InspectionOrderResponse> getOrderById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID orderId) {
        UUID actorId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(inspectionService.getOrderById(actorId, orderId));
    }

    @GetMapping("/my-buyer-orders")
    @Operation(summary = "Người mua xem danh sách đơn mua có kiểm định của mình")
    public ResponseEntity<com.secondlife.secondlife.common.ApiResponse<com.secondlife.secondlife.common.PageResponse<InspectionOrderResponse>>> getMyBuyerOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Pageable pageable) {
        UUID buyerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(com.secondlife.secondlife.common.ApiResponse.success(
                com.secondlife.secondlife.common.PageResponse.from(inspectionService.getBuyerOrders(buyerId, pageable))));
    }

    @GetMapping("/my-seller-orders")
    @Operation(summary = "Người bán xem danh sách đơn bán có kiểm định của mình")
    public ResponseEntity<com.secondlife.secondlife.common.ApiResponse<com.secondlife.secondlife.common.PageResponse<InspectionOrderResponse>>> getMySellerOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Pageable pageable) {
        UUID sellerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(com.secondlife.secondlife.common.ApiResponse.success(
                com.secondlife.secondlife.common.PageResponse.from(inspectionService.getSellerOrders(sellerId, pageable))));
    }

    @PostMapping("/orders/{orderId}/confirm-purchase")
    @Operation(summary = "Người mua thanh toán tiền hàng và ship cuối cùng sau khi sản phẩm kiểm định PASSED (trong vòng 6h)")
    public ResponseEntity<InspectionOrderResponse> confirmFinalPurchase(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID orderId) {
        UUID buyerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(inspectionService.confirmFinalPurchase(buyerId, orderId));
    }
}
