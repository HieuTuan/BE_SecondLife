package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.CreateReportSubmissionRequest;
import com.secondlife.secondlife.dto.response.ReportSubmissionResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.ReportSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/reports/{reportId}/responses")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Order Report Responses")
public class ReportSubmissionController {
    private final ReportSubmissionService submissions;
    private final CurrentUserProvider currentUser;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Append your packaging proof or supplemental evidence to an open order report",
            description = "Only the reporter or seller may submit. Private evidence must belong to the author; requestId protects retries.")
    public ApiResponse<ReportSubmissionResponse> create(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID reportId, @Valid @RequestBody CreateReportSubmissionRequest request) {
        return ApiResponse.success(submissions.create(currentUser.resolveUserId(user), reportId, request));
    }

    @GetMapping
    @Operation(summary = "List your own responses; authorized fraud reviewers can compare both parties",
            description = "Pages are ordered by creation time and ID, with at most 100 responses per page.")
    public ApiResponse<PageResponse<ReportSubmissionResponse>> list(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID reportId, Pageable pageable) {
        UUID actorId = currentUser.resolveUserId(user);
        Set<String> authorities = user.getAuthorities().stream().map(authority -> authority.getAuthority()).collect(Collectors.toSet());
        boolean reviewer = (authorities.contains("ROLE_STAFF") && authorities.contains("STAFF_FRAUD_REVIEW"))
                || (authorities.contains("ROLE_ADMIN") && authorities.contains("ADMIN_RBAC_MANAGE"));
        return ApiResponse.success(submissions.list(actorId, reviewer, reportId, pageable));
    }
}
