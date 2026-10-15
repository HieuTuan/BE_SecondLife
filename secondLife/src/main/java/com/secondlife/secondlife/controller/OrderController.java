package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.OrderRequestDTO;
import com.secondlife.secondlife.dto.response.OrderResponseDTO;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final CurrentUserProvider currentUserProvider;
    private final com.secondlife.secondlife.service.payment.OrderBankPaymentService bankPayments;

    @GetMapping("/{id}/payment")
    @Operation(summary="BUYER reads bank transfer instructions and polls payment status", description="Read only; payment is confirmed exclusively by authenticated SePay webhooks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<com.secondlife.secondlife.dto.response.OrderBankPaymentResponse> payment(
            @AuthenticationPrincipal CustomUserDetails userDetails,@PathVariable UUID id) {
        return ResponseEntity.ok(bankPayments.get(currentUserProvider.resolveUserId(userDetails),id));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponseDTO> createOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody OrderRequestDTO requestDTO) {
        UUID buyerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(orderService.createOrder(buyerId, requestDTO));
    }

    @GetMapping("/buyer")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<OrderResponseDTO>> getBuyerOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ParameterObject @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID buyerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(orderService.getBuyerOrders(buyerId, pageable));
    }

    @GetMapping("/seller")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<OrderResponseDTO>> getSellerOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ParameterObject @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID sellerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(orderService.getSellerOrders(sellerId, pageable));
    }

    @PutMapping("/{id}/shipped")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponseDTO> markAsShipped(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        UUID sellerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(orderService.markAsShipped(sellerId, id));
    }

    @PutMapping("/{id}/delivered")
    @Operation(summary = "BUYER confirms receipt and releases net seller payout immediately",
            description = "Requires carrier-confirmed delivery and HELD escrow. Safe to retry; frozen or returned orders cannot be released.")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponseDTO> confirmDelivery(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        UUID buyerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(orderService.confirmDelivery(buyerId, id));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary="Cancel unshipped order; SELLER must supply SELLER_OUT_OF_STOCK or SELLER_CANNOT_FULFILL reason, BUYER may omit body")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponseDTO> cancelOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,@Valid @RequestBody(required=false) com.secondlife.secondlife.dto.reputation.CancelOrderRequest request) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(orderService.cancelOrder(userId,id,request));
    }
}
