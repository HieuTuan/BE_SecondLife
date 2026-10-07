package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.OrderRequestDTO;
import com.secondlife.secondlife.dto.response.OrderResponseDTO;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.OrderService;
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponseDTO> confirmDelivery(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        UUID buyerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(orderService.confirmDelivery(buyerId, id));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponseDTO> cancelOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(orderService.cancelOrder(userId, id));
    }
}
