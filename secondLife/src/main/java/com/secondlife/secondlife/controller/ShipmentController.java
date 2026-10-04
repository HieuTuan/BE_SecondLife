package com.secondlife.secondlife.controller;
import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.shipping.*;
import com.secondlife.secondlife.entity.ShipmentEvent;
import com.secondlife.secondlife.service.shipping.*;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import java.util.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1")
public class ShipmentController {
    private final ShipmentService shipments;
    private final ShippingQuoteService quotes;
    private final CurrentUserProvider current;
    private UUID actor(CustomUserDetails user) { return current.resolveUserId(user); }
    private boolean staff() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> Set.of("ROLE_STAFF","ROLE_ADMIN").contains(a.getAuthority()));
    }
    @PutMapping("/posts/{postId}/shipping-package") @PreAuthorize("hasRole('SELLER') and hasAuthority('LISTING_CREATE_SELF')")
    public ApiResponse<ShippingParcel> parcel(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID postId,@Valid @RequestBody ShippingParcel body) {
        return ApiResponse.success(quotes.saveParcel(actor(user),postId,body));
    }
    @PostMapping("/orders/{orderId}/shipments")
    public ApiResponse<ShipmentResponse> create(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID orderId,@Valid @RequestBody CreateShipmentRequest body) {
        return ApiResponse.success(shipments.createForOrder(actor(user),staff(),orderId,body));
    }
    @PostMapping("/orders/{orderId}/shipments/import") @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @io.swagger.v3.oas.annotations.Operation(summary="Link a verified GHN shipment to a legacy order", description="Only for unsettled orders created before shipping quotes existed. STAFF verifies that this carrier shipment belongs to the order; reason is audited. No additional buyer fee is charged.")
    public ApiResponse<ShipmentResponse> importLegacy(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID orderId,@Valid @RequestBody ImportShipmentRequest body) {
        return ApiResponse.success(shipments.importLegacy(actor(user),staff(),orderId,body));
    }
    @GetMapping("/orders/{orderId}/shipments")
    public ApiResponse<List<ShipmentResponse>> order(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID orderId) {
        return ApiResponse.success(shipments.orderShipments(actor(user),staff(),orderId));
    }
    @PostMapping("/inspection-orders/{inspectionId}/shipments") @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    public ApiResponse<ShipmentResponse> inspectionCreate(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID inspectionId,@Valid @RequestBody CreateShipmentRequest body) {
        return ApiResponse.success(shipments.createForInspection(actor(user),staff(),inspectionId,body));
    }
    @GetMapping("/inspection-orders/{inspectionId}/shipments")
    public ApiResponse<List<ShipmentResponse>> inspection(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID inspectionId) {
        return ApiResponse.success(shipments.inspectionShipments(actor(user),staff(),inspectionId));
    }
    @GetMapping("/shipments/{shipmentId}/events")
    public ApiResponse<List<ShipmentEvent>> events(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID shipmentId) {
        return ApiResponse.success(shipments.timeline(actor(user),staff(),shipmentId));
    }
    @GetMapping("/shipments/{shipmentId}/label")
    public ApiResponse<JsonNode> label(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID shipmentId) {
        return ApiResponse.success(shipments.label(actor(user),staff(),shipmentId));
    }
    @PostMapping("/shipments/{shipmentId}/cancel")
    public ApiResponse<ShipmentResponse> cancel(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID shipmentId) {
        return ApiResponse.success(shipments.cancel(actor(user),staff(),shipmentId));
    }
    @PostMapping("/shipments/{shipmentId}/sync")
    public ApiResponse<ShipmentResponse> sync(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID shipmentId) {
        return ApiResponse.success(shipments.sync(actor(user),staff(),shipmentId));
    }
    @PostMapping("/shipments/{shipmentId}/return-to-sender") @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    public ApiResponse<ShipmentResponse> returnToSender(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID shipmentId,@Valid @RequestBody ShippingDecisionRequest body) {
        return ApiResponse.success(shipments.returnToSender(actor(user),staff(),shipmentId,body.reason()));
    }
    @PostMapping("/shipments/{shipmentId}/refund") @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    public ApiResponse<ShipmentResponse> refund(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID shipmentId,@Valid @RequestBody ShippingDecisionRequest body) {
        return ApiResponse.success(shipments.refundReturned(actor(user),staff(),shipmentId,body.reason()));
    }
}
