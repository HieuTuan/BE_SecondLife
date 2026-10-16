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
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Set;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/reports") @RequiredArgsConstructor
@PreAuthorize("isAuthenticated()") @Tag(name="Reports")
public class UserReportController {
    private final UserReportService reports;
    private final CurrentUserProvider currentUser;
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Report a public listing or your purchased order; requestId protects retries")
    public ApiResponse<ReportResponse> create(@AuthenticationPrincipal CustomUserDetails user,@Valid @RequestBody ReportRequests.Create request) {
        return ApiResponse.success(reports.create(currentUser.resolveUserId(user),request));
    }
    @PostMapping("/listings/{postId}") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Báo cáo vi phạm bài đăng: nghi ngờ lừa đảo, sai thông tin, trùng bài hoặc vi phạm khác",
            description="Không cần mua hàng. Upload bằng chứng trước qua POST /api/v1/reports/evidence. Report mới chưa làm giảm điểm người bán.")
    public ApiResponse<ReportResponse> createListing(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID postId,@Valid @RequestBody ReportRequests.ListingCreate request) {
        return ApiResponse.success(reports.createListing(currentUser.resolveUserId(user),postId,request));
    }
    @GetMapping("/me")
    public ApiResponse<PageResponse<ReportResponse>> mine(@AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required=false) ReportRequests.TargetType targetType,Pageable pageable) {
        return ApiResponse.success(reports.mine(currentUser.resolveUserId(user),targetType,pageable));
    }
    @GetMapping("/{id}")
    @Operation(summary="Private report detail; only reporter or authorized STAFF/ADMIN")
    public ApiResponse<ReportResponse> get(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID id) {
        Set<String> authorities=user.getAuthorities().stream().map(a->a.getAuthority()).collect(java.util.stream.Collectors.toSet());
        boolean reviewer=(authorities.contains("ROLE_STAFF") && authorities.contains("STAFF_FRAUD_REVIEW"))
                || (authorities.contains("ROLE_ADMIN") && authorities.contains("ADMIN_RBAC_MANAGE"));
        return ApiResponse.success(reports.get(currentUser.resolveUserId(user),reviewer,id));
    }
}
