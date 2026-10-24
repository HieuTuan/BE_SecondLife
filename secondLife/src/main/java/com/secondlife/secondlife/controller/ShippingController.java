package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.CoordinatesRequest;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.request.ShippingAddressRequest;
import com.secondlife.secondlife.dto.request.ShippingQuoteRequest;
import com.secondlife.secondlife.dto.response.GeocodedAddressResponse;
import com.secondlife.secondlife.dto.response.ShippingQuoteResponse;
import com.secondlife.secondlife.dto.response.ShippingVehicleCatalogResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.ReverseGeocodingService;
import com.secondlife.secondlife.service.ShippingCatalogService;
import com.secondlife.secondlife.service.ShippingQuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

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
    @GetMapping("/vehicles")
    @Operation(summary = "Get normalized Lalamove vehicle limits for the shipping form",
            description = "Returns all configured cities, or the pickup city when latitude and longitude are supplied together. "
                    + "Minimum weight is an application input rule; maximum capacities come from Lalamove. Route and price require a quotation.")
    public ApiResponse<ShippingVehicleCatalogResponse> vehicles(
            @Parameter(description = "Pickup latitude; supply together with longitude", example = "10.777")
            @RequestParam(name = "latitude", required = false) BigDecimal latitude,
            @Parameter(description = "Pickup longitude; supply together with latitude", example = "106.695")
            @RequestParam(name = "longitude", required = false) BigDecimal longitude) {
        return ApiResponse.success(catalog.vehicles(latitude, longitude));
    }

    @GetMapping("/provinces")
    @Operation(description="Alias of /cities: Lalamove city catalogue restricted to the configured allowed cities.")
    public ApiResponse<JsonNode> provinces() { return ApiResponse.success(catalog.provinces()); }
    @PutMapping("/pickup-address") @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<ShippingAddress> savePickup(@AuthenticationPrincipal CustomUserDetails user,@Valid @RequestBody ShippingAddressRequest body) {
        return ApiResponse.success(quotes.savePickup(current.resolveUserId(user),geocoding.shipping(body)));
    }
    @GetMapping("/pickup-address") @PreAuthorize("hasAnyRole('SELLER', 'BUYER')")
    public ApiResponse<ShippingAddress> pickup(@AuthenticationPrincipal CustomUserDetails user) { return ApiResponse.success(quotes.pickup(current.resolveUserId(user))); }
    @PostMapping("/quotes") @PreAuthorize("hasRole('BUYER') or hasRole('SELLER')")
    @Operation(summary="Tự chọn xe phù hợp và lấy phí vận chuyển",
        description="BUYER gửi bài đăng và địa chỉ nhận. BE lọc tải trọng/kích thước kiện hàng theo danh mục xe Lalamove, ưu tiên xe có tải trọng nhỏ nhất đủ chở kiện hàng và lấy báo giá đầu tiên thành công. serviceType trong response do BE quyết định.")
    public ApiResponse<ShippingQuoteResponse> quote(@AuthenticationPrincipal CustomUserDetails user,@Valid @RequestBody ShippingQuoteRequest body) {
        return ApiResponse.success(quotes.quote(current.resolveUserId(user),body));
    }
}
