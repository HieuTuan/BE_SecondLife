package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.CoordinatesRequest;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.request.ShippingAddressRequest;
import com.secondlife.secondlife.dto.request.ShippingQuoteRequest;
import com.secondlife.secondlife.dto.response.GeocodedAddressResponse;
import com.secondlife.secondlife.dto.response.ShippingQuoteResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.ReverseGeocodingService;
import com.secondlife.secondlife.service.ShippingCatalogService;
import com.secondlife.secondlife.service.ShippingQuoteService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shipping")
public class ShippingController {
    private final ShippingCatalogService catalog;
    private final ShippingQuoteService quotes;
    private final CurrentUserProvider current;
    private final ReverseGeocodingService geocoding;
    @PostMapping("/reverse-geocode") @PreAuthorize("isAuthenticated()")
    @Operation(summary="Preview an address from coordinates",description="Resolves an address inside an allowed Vietnamese city. Address writes perform their own lookup.")
    public ApiResponse<GeocodedAddressResponse> reverse(@Valid @RequestBody CoordinatesRequest body) {
        return ApiResponse.success(geocoding.reverse(body.latitude(),body.longitude()));
    }
    @GetMapping("/cities") public ApiResponse<JsonNode> cities() { return ApiResponse.success(catalog.cities()); }
    @GetMapping("/provinces")
    @Operation(description="Alias of /cities: Lalamove city catalogue restricted to the configured allowed cities.")
    public ApiResponse<JsonNode> provinces() { return ApiResponse.success(catalog.provinces()); }
    @PutMapping("/pickup-address") @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<ShippingAddress> savePickup(@AuthenticationPrincipal CustomUserDetails user,@Valid @RequestBody ShippingAddressRequest body) {
        return ApiResponse.success(quotes.savePickup(current.resolveUserId(user),geocoding.shipping(body)));
    }
    @GetMapping("/pickup-address") @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<ShippingAddress> pickup(@AuthenticationPrincipal CustomUserDetails user) { return ApiResponse.success(quotes.pickup(current.resolveUserId(user))); }
    @PostMapping("/quotes") @PreAuthorize("hasRole('BUYER') or hasRole('SELLER')")
    @Operation(summary="Tự chọn xe phù hợp và lấy phí vận chuyển",
        description="BUYER gửi bài đăng và địa chỉ nhận. BE lọc tải trọng/kích thước kiện hàng theo danh mục xe Lalamove, chọn báo giá thấp nhất. serviceType trong response do BE quyết định.")
    public ApiResponse<ShippingQuoteResponse> quote(@AuthenticationPrincipal CustomUserDetails user,@Valid @RequestBody ShippingQuoteRequest body) {
        return ApiResponse.success(quotes.quote(current.resolveUserId(user),body));
    }
}
