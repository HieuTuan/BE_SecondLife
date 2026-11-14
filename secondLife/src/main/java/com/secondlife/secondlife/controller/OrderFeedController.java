package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.OrderFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders/feed")
@RequiredArgsConstructor
public class OrderFeedController {
    private final OrderFeedService orders;
    private final CurrentUserProvider users;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<OrderFeedService.Feed> read(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "all") String perspective,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) UUID orderId) {
        return ApiResponse.success(orders.read(users.resolveUserId(user), perspective, filter, q, page, size, orderId));
    }
}
