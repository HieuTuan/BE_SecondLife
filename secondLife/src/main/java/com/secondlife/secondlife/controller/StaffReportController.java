package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.ReportRequests;
import com.secondlife.secondlife.dto.response.ReportResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.UserReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController @RequestMapping("/api/v1/staff/reports") @RequiredArgsConstructor
@PreAuthorize("(hasRole('STAFF') and hasAuthority('STAFF_FRAUD_REVIEW')) or (hasRole('ADMIN') and hasAuthority('ADMIN_RBAC_MANAGE'))")
@Tag(name="Staff - Report Review")
public class StaffReportController {
    private final UserReportService reports;
    private final CurrentUserProvider currentUser;
    @GetMapping
    @Operation(summary="List reports newest first", description="Fixed order: createdAt DESC, id DESC. No sort parameter is needed or applied.")
    @Parameters({
        @Parameter(name="page",in=ParameterIn.QUERY,description="Zero-based page; defaults to app.pagination.default-page",example="0",schema=@Schema(type="integer",minimum="0")),
        @Parameter(name="size",in=ParameterIn.QUERY,description="Page size; default and maximum follow app.pagination configuration",example="20",schema=@Schema(type="integer",minimum="1"))
    })
    public ApiResponse<PageResponse<ReportResponse>> queue(@RequestParam(required=false) ReportRequests.Status status,
            @RequestParam(required=false) ReportRequests.TargetType targetType,@Parameter(hidden=true) Pageable pageable) {
        return ApiResponse.success(reports.queue(status,targetType,pageable));
    }
    @PostMapping("/{id}/start-review")
    @Operation(summary="Assign OPEN report to the current reviewer; other staff cannot resolve it")
    public ApiResponse<ReportResponse> start(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID id) {
        return ApiResponse.success(reports.startReview(currentUser.resolveUserId(user),id));
    }
    @PostMapping("/{id}/resolve")
    @PreAuthorize("((hasRole('STAFF') and hasAuthority('STAFF_FRAUD_REVIEW')) or (hasRole('ADMIN') and hasAuthority('ADMIN_RBAC_MANAGE'))) and (#request.resolutionAction().name() != 'RETURN_AND_REFUND' or (hasRole('STAFF') and hasAuthority('STAFF_DISPUTE_REVIEW')) or (hasRole('ADMIN') and hasAuthority('ADMIN_RBAC_MANAGE')))")
    @Operation(summary="Confirm responsible party or dismiss; only confirmed SELLER fault changes reputation, repeated confirmed FRAUD restricts new sales")
    public ApiResponse<ReportResponse> resolve(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID id,
            @Valid @RequestBody ReportRequests.Resolve request) {
        return ApiResponse.success(reports.resolve(currentUser.resolveUserId(user),id,request));
    }
}
