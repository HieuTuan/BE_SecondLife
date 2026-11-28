package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.AcceptPolicyRequest;
import com.secondlife.secondlife.dto.response.PolicyAcceptanceStatusResponse;
import com.secondlife.secondlife.dto.response.PolicyVersionResponse;
import com.secondlife.secondlife.dto.response.PolicyVersionSummaryResponse;
import com.secondlife.secondlife.enums.PolicyType;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.PolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
@Tag(name = "Policies & Terms", description = "Public Terms of Service reading and authenticated user policy acceptance APIs")
public class PolicyController {

    private final PolicyService policyService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/current")
    @Operation(summary = "Get the currently active policy document (public)")
    public ResponseEntity<ApiResponse<PolicyVersionResponse>> getCurrentPolicy(
            @RequestParam(defaultValue = "TERMS_OF_SERVICE") PolicyType type
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Current policy retrieved successfully",
                policyService.getCurrentPolicy(type)
        ));
    }

    @GetMapping("/versions")
    @Operation(summary = "List all published policy versions (public)")
    public ResponseEntity<ApiResponse<List<PolicyVersionSummaryResponse>>> listPublishedVersions(
            @RequestParam(defaultValue = "TERMS_OF_SERVICE") PolicyType type
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Published policy versions retrieved successfully",
                policyService.listPublishedVersions(type)
        ));
    }

    @GetMapping("/versions/{versionId}")
    @Operation(summary = "Get a specific published policy version by ID (public)")
    public ResponseEntity<ApiResponse<PolicyVersionResponse>> getPolicyVersion(@PathVariable UUID versionId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Policy version retrieved successfully",
                policyService.getPublishedPolicyVersion(versionId)
        ));
    }

    @GetMapping("/acceptance/status")
    @Operation(summary = "Check whether the authenticated user has accepted the active policy version")
    public ResponseEntity<ApiResponse<PolicyAcceptanceStatusResponse>> getAcceptanceStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "TERMS_OF_SERVICE") PolicyType type
    ) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                "Policy acceptance status retrieved successfully",
                policyService.getAcceptanceStatus(userId, type)
        ));
    }

    @PostMapping("/acceptance")
    @Operation(summary = "Record explicit acceptance of the active policy version for the authenticated user")
    public ResponseEntity<ApiResponse<PolicyAcceptanceStatusResponse>> acceptPolicy(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AcceptPolicyRequest request
    ) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                "Policy acceptance recorded successfully",
                policyService.acceptCurrentPolicy(userId, request)
        ));
    }
}
