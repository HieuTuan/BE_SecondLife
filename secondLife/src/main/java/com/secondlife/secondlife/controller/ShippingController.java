package com.secondlife.secondlife.controller;
import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.shipping.*;
import com.secondlife.secondlife.service.shipping.*;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import tools.jackson.databind.JsonNode;
import java.util.UUID;
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shipping")
public class ShippingController {
    private final ShippingCatalogService catalog;
    private final ShippingQuoteService quotes;
    private final CurrentUserProvider current;
    private final ReverseGeocodingService geocoding;
    @PostMapping("/reverse-geocode") @PreAuthorize("isAuthenticated()")
    @io.swagger.v3.oas.annotations.Operation(summary="Preview an address from coordinates",description="Resolves an address inside an allowed Vietnamese city. Address writes perform their own lookup.")
    public ApiResponse<GeocodedAddressResponse> reverse(@Valid @RequestBody CoordinatesRequest body) {
        return ApiResponse.success(geocoding.reverse(body.latitude(),body.longitude()));
    }
    @GetMapping("/cities") public ApiResponse<JsonNode> cities() { return ApiResponse.success(catalog.cities()); }
    @GetMapping("/provinces")
    @io.swagger.v3.oas.annotations.Operation(description="Alias of /cities: Lalamove city catalogue restricted to the configured allowed cities.")
    public ApiResponse<JsonNode> provinces() { return ApiResponse.success(catalog.provinces()); }
    @PutMapping("/pickup-address") @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<ShippingAddress> savePickup(@AuthenticationPrincipal CustomUserDetails user,@Valid @RequestBody ShippingAddressRequest body) {
        return ApiResponse.success(quotes.savePickup(current.resolveUserId(user),geocoding.shipping(body)));
    }
    @GetMapping("/pickup-address") @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<ShippingAddress> pickup(@AuthenticationPrincipal CustomUserDetails user) { return ApiResponse.success(quotes.pickup(current.resolveUserId(user))); }
    @PostMapping("/quotes") @PreAuthorize("hasRole('BUYER') or hasRole('SELLER')")
    public ApiResponse<ShippingQuoteResponse> quote(@AuthenticationPrincipal CustomUserDetails user,@Valid @RequestBody ShippingQuoteRequest body) {
        return ApiResponse.success(quotes.quote(current.resolveUserId(user),body));
    }
}
