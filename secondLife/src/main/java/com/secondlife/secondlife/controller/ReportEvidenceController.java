package com.secondlife.secondlife.controller;
import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.response.ReportEvidenceUploadResponse;
import com.secondlife.secondlife.security.*;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.ReportEvidenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController @RequestMapping("/api/v1/reports/evidence") @RequiredArgsConstructor
@PreAuthorize("isAuthenticated()") @Tag(name="Reports - Private Evidence")
public class ReportEvidenceController {
    private final ReportEvidenceService evidence;
    private final CurrentUserProvider currentUser;
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MEDIA_UPLOAD_SELF')")
    @Operation(summary="Upload private evidence; use returned evidenceUrl in POST /reports evidenceUrls")
    public ApiResponse<ReportEvidenceUploadResponse> upload(@AuthenticationPrincipal CustomUserDetails user,@RequestParam MultipartFile file) {
        return ApiResponse.success(evidence.upload(currentUser.resolveUserId(user),file));
    }
    @GetMapping("/{id}")
    @Operation(summary="Private image bytes; FE fetches with bearer token then creates an object URL for display")
    public ResponseEntity<byte[]> get(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID id) {
        Set<String> a=user.getAuthorities().stream().map(x->x.getAuthority()).collect(Collectors.toSet());
        boolean reviewer=(a.contains("ROLE_STAFF") && a.contains("STAFF_FRAUD_REVIEW")) || (a.contains("ROLE_ADMIN") && a.contains("ADMIN_RBAC_MANAGE"));
        var image=evidence.read(currentUser.resolveUserId(user),reviewer,id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options","nosniff").body(image.bytes());
    }
}
