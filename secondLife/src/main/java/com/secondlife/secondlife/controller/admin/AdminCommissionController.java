package com.secondlife.secondlife.controller.admin;

import com.secondlife.secondlife.common.*;
import com.secondlife.secondlife.dto.commission.*;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.CommissionService;
import com.secondlife.secondlife.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/admin") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_COMMISSION_MANAGE')")
@Tag(name = "Admin - Commission", description = "Default commission for all products with optional fee limits. Rates are fractions; 0.05 means 5%.")
public class AdminCommissionController {
    private final CommissionService commissions;
    private final SettlementService settlements;
    private final CurrentUserProvider currentUser;

    @GetMapping("/commission-rules")
    public ApiResponse<PageResponse<CommissionRuleResponse>> list(@ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.from(commissions.list(safePage(pageable))));
    }
    @GetMapping("/commission-rules/{id}")
    public ApiResponse<CommissionRuleResponse> get(@PathVariable UUID id) { return ApiResponse.success(commissions.get(id)); }
    @PostMapping("/commission-rules")
    @Operation(summary = "Create a default commission policy with minimum and optional maximum fee")
    public ResponseEntity<ApiResponse<CommissionRuleResponse>> create(@AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CommissionRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(commissions.create(currentUser.resolveAdminId(user), request)));
    }
    @PutMapping("/commission-rules/{id}")
    @Operation(summary = "Update policy for future orders; existing order snapshots are unchanged")
    public ApiResponse<CommissionRuleResponse> update(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id,
            @Valid @RequestBody CommissionRuleRequest request) {
        return ApiResponse.success(commissions.update(currentUser.resolveAdminId(user), id, request));
    }
    @PostMapping("/commission-rules/{id}/deactivate")
    @Operation(summary = "Disable policy for future orders; retain financial history")
    public ApiResponse<CommissionRuleResponse> deactivate(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id,
            @Valid @RequestBody CommissionReasonRequest request) {
        return ApiResponse.success(commissions.deactivate(currentUser.resolveAdminId(user), id, request.reason()));
    }
    @GetMapping("/commission-rules/{id}/history")
    public ApiResponse<PageResponse<CommissionAuditResponse>> history(@PathVariable UUID id, @ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.from(commissions.history(id, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))));
    }
    @PostMapping("/orders/{orderId}/commission-snapshot")
    @Operation(summary = "Capture current policy for an unsettled legacy order missing a snapshot; requires an audit reason")
    public ApiResponse<OrderCommissionResponse> captureLegacy(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID orderId,
            @Valid @RequestBody CommissionReasonRequest request) {
        return ApiResponse.success(settlements.captureLegacy(currentUser.resolveAdminId(user), orderId, request.reason()));
    }
    private Pageable safePage(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }
}
