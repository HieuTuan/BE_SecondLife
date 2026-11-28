package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.PublishPolicyVersionRequest;
import com.secondlife.secondlife.dto.response.PolicyVersionResponse;
import com.secondlife.secondlife.service.PolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/policies")
@RequiredArgsConstructor
@Tag(name = "Admin Policies", description = "Administrative policy version publication APIs")
public class AdminPolicyController {

    private final PolicyService policyService;

    @PostMapping("/versions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Publish a new immutable policy version and archive the previous active version")
    public ResponseEntity<ApiResponse<PolicyVersionResponse>> publishNewVersion(
            @Valid @RequestBody PublishPolicyVersionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Policy version published successfully",
                policyService.publishNewVersion(request)
        ));
    }
}
