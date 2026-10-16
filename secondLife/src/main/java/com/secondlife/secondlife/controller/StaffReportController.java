package com.secondlife.secondlife.controller;
import com.secondlife.secondlife.common.*;
import com.secondlife.secondlife.dto.reputation.*;
import com.secondlife.secondlife.security.*;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.UserReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/staff/reports") @RequiredArgsConstructor
@PreAuthorize("(hasRole('STAFF') and hasAuthority('STAFF_FRAUD_REVIEW')) or (hasRole('ADMIN') and hasAuthority('ADMIN_RBAC_MANAGE'))")
@Tag(name="Staff - Report Review")
public class StaffReportController {
    private final UserReportService reports;
    private final CurrentUserProvider currentUser;
    @GetMapping
    public ApiResponse<PageResponse<ReportResponse>> queue(@RequestParam(required=false) ReportRequests.Status status,
            @RequestParam(required=false) ReportRequests.TargetType targetType,Pageable pageable) {
        return ApiResponse.success(reports.queue(status,targetType,pageable));
    }
    @PostMapping("/{id}/start-review")
    @Operation(summary="Assign OPEN report to the current reviewer; other staff cannot resolve it")
    public ApiResponse<ReportResponse> start(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID id) {
        return ApiResponse.success(reports.startReview(currentUser.resolveUserId(user),id));
    }
    @PostMapping("/{id}/resolve")
    @Operation(summary="Confirm responsible party or dismiss; only confirmed SELLER fault changes reputation, repeated confirmed FRAUD restricts new sales")
    public ApiResponse<ReportResponse> resolve(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID id,
            @Valid @RequestBody ReportRequests.Resolve request) {
        return ApiResponse.success(reports.resolve(currentUser.resolveUserId(user),id,request));
    }
}
