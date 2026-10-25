package com.secondlife.secondlife.controller;
import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.VideoCompleteRequest;
import com.secondlife.secondlife.dto.request.VideoSignatureRequest;
import com.secondlife.secondlife.dto.response.VideoMetadataResponse;
import com.secondlife.secondlife.dto.response.VideoPlaybackResponse;
import com.secondlife.secondlife.dto.response.VideoSignatureResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.VideoUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
@RestController @RequestMapping("/api/video") @RequiredArgsConstructor
@Tag(name="Video - Direct chunked evidence upload")
public class VideoUploadController {
    private final VideoUploadService videos;
    private final CurrentUserProvider current;
    @PostMapping("/upload-signature") @PreAuthorize("hasAuthority('MEDIA_UPLOAD_SELF')")
    @Operation(summary="Create an owned signed Cloudinary chunked video upload; bytes go directly to Cloudinary")
    public ResponseEntity<ApiResponse<VideoSignatureResponse>> signature(@AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody VideoSignatureRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(videos.signature(current.resolveUserId(user),request)));
    }
    @PostMapping("/complete") @PreAuthorize("hasAuthority('MEDIA_UPLOAD_SELF')")
    @Operation(summary="Verify completed video against Cloudinary and register private report evidence")
    public ApiResponse<VideoMetadataResponse> complete(@AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody VideoCompleteRequest request) {
        return ApiResponse.success(videos.complete(current.resolveUserId(user),request));
    }
    @GetMapping("/{uploadId}/playback") @PreAuthorize("isAuthenticated()")
    @Operation(summary="Authorize private video viewing and return an expiring Cloudinary download URL")
    public ResponseEntity<ApiResponse<VideoPlaybackResponse>> playback(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID uploadId) {
        Set<String> authorities=user.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toSet());
        boolean reviewer=(authorities.contains("ROLE_STAFF") && authorities.contains("STAFF_FRAUD_REVIEW"))
                || (authorities.contains("ROLE_ADMIN") && authorities.contains("ADMIN_RBAC_MANAGE"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(videos.playback(current.resolveUserId(user),reviewer,uploadId)));
    }
}
