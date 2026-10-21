package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.CreateShipmentRequest;
import com.secondlife.secondlife.dto.request.ReconcileShipmentRequest;
import com.secondlife.secondlife.dto.request.ShippingDecisionRequest;
import com.secondlife.secondlife.dto.request.ShippingParcel;
import com.secondlife.secondlife.dto.response.ShipmentEventResponse;
import com.secondlife.secondlife.dto.response.ShipmentResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.ShipmentService;
import com.secondlife.secondlife.service.ShipmentTimelineService;
import com.secondlife.secondlife.service.ShippingQuoteService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1")
public class ShipmentController {
    private final ShipmentService shipments;
    private final ShipmentTimelineService timeline;
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
    @PreAuthorize("#body.leg() != 'BUYER_TO_SELLER' or (hasRole('STAFF') and hasAuthority('STAFF_DISPUTE_REVIEW')) or (hasRole('ADMIN') and hasAuthority('ADMIN_RBAC_MANAGE'))")
    public ApiResponse<ShipmentResponse> create(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID orderId,@Valid @RequestBody CreateShipmentRequest body) {
        return ApiResponse.success(shipments.createForOrder(actor(user),staff(),orderId,body));
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
    public ApiResponse<List<ShipmentEventResponse>> events(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID shipmentId) {
        return ApiResponse.success(timeline.getTimeline(actor(user),staff(),shipmentId));
    }
    @GetMapping("/shipments/{shipmentId}/tracking")
    @Operation(summary="Track shipment and available Lalamove driver location", description="Only shipment participants or STAFF/ADMIN. Coordinates may be unavailable before driver arrival or after completion; poll at most once every 10 seconds.")
    public ApiResponse<JsonNode> tracking(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID shipmentId) {
        return ApiResponse.success(shipments.tracking(actor(user),staff(),shipmentId));
    }
    @PostMapping("/shipments/{shipmentId}/reconcile-creation") @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary="Recover an uncertain Lalamove booking", description="Supply the existing carrier orderCode from Lalamove Sandbox. Backend verifies the stored client reference using authenticated carrier detail; this API creates no new carrier order.")
    public ApiResponse<ShipmentResponse> reconcile(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID shipmentId,@Valid @RequestBody ReconcileShipmentRequest body) {
        return ApiResponse.success(shipments.reconcileCreation(actor(user),staff(),shipmentId,body.orderCode()));
    }
    @PostMapping("/shipments/{shipmentId}/cancel")
    public ApiResponse<ShipmentResponse> cancel(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID shipmentId) {
        return ApiResponse.success(shipments.cancel(actor(user),staff(),shipmentId));
    }
    @PostMapping("/shipments/{shipmentId}/sync")
    public ApiResponse<ShipmentResponse> sync(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID shipmentId) {
        return ApiResponse.success(shipments.sync(actor(user),staff(),shipmentId));
    }
    @PostMapping("/shipments/{shipmentId}/refund") @PreAuthorize("(hasRole('STAFF') and hasAuthority('STAFF_DISPUTE_REVIEW')) or (hasRole('ADMIN') and hasAuthority('ADMIN_RBAC_MANAGE'))")
    public ApiResponse<ShipmentResponse> refund(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID shipmentId,@Valid @RequestBody ShippingDecisionRequest body) {
        return ApiResponse.success(shipments.refundReturned(actor(user),staff(),shipmentId,body.reason(),body.responsibleParty()));
    }
}
