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
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/shipping")
public class ShippingController {
    private final ShippingProvider provider;
    private final ShippingQuoteService quotes;
    private final ShipmentService shipments;
    private final CurrentUserProvider current;
    @GetMapping("/provinces") public ApiResponse<JsonNode> provinces() { return ApiResponse.success(provider.provinces()); }
    @GetMapping("/wards") public ApiResponse<JsonNode> wards(@RequestParam int provinceId) { return ApiResponse.success(provider.wards(provinceId)); }
    @GetMapping("/legacy/provinces") public ApiResponse<JsonNode> legacyProvinces() { return ApiResponse.success(provider.legacyProvinces()); }
    @GetMapping("/legacy/districts") public ApiResponse<JsonNode> districts(@RequestParam int provinceId) { return ApiResponse.success(provider.districts(provinceId)); }
    @GetMapping("/legacy/wards") public ApiResponse<JsonNode> legacyWards(@RequestParam int districtId) { return ApiResponse.success(provider.legacyWards(districtId)); }
    @GetMapping("/services") public ApiResponse<JsonNode> services(@RequestParam int fromDistrictId,@RequestParam int toDistrictId) { return ApiResponse.success(provider.services(fromDistrictId,toDistrictId)); }
    @PutMapping("/pickup-address") @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<ShippingAddress> savePickup(@AuthenticationPrincipal CustomUserDetails user,@Valid @RequestBody ShippingAddress body) {
        return ApiResponse.success(quotes.savePickup(current.resolveUserId(user),body));
    }
    @GetMapping("/pickup-address") @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<ShippingAddress> pickup(@AuthenticationPrincipal CustomUserDetails user) { return ApiResponse.success(quotes.pickup(current.resolveUserId(user))); }
    @PostMapping("/quotes") @PreAuthorize("hasRole('BUYER') or hasRole('SELLER')")
    public ApiResponse<ShippingQuoteResponse> quote(@AuthenticationPrincipal CustomUserDetails user,@Valid @RequestBody ShippingQuoteRequest body) {
        return ApiResponse.success(quotes.quote(current.resolveUserId(user),body));
    }
    @PostMapping("/callback")
    public ApiResponse<Void> callback(@RequestHeader(value="X-GHN-Secret",required=false) String secret,@RequestBody JsonNode body) {
        shipments.verifyWebhook(secret); shipments.receive(body); return ApiResponse.success(null);
    }
}
