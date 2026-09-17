package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.ChangePasswordRequest;
import com.secondlife.secondlife.dto.request.UpdateProfileRequest;
import com.secondlife.secondlife.dto.response.UserProfileResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "User profile and password management APIs")
public class UserProfileController {

    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "Get current user profile")
    @PreAuthorize("hasAuthority('PROFILE_READ_SELF')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        UserProfileResponse response = userService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success("Get profile successfully", response));
    }

    @PatchMapping
    @Operation(summary = "Update current user profile")
    @PreAuthorize("hasAuthority('PROFILE_UPDATE_SELF')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        UserProfileResponse response = userService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", response));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change own password and revoke active sessions")
    @PreAuthorize("hasAuthority('PASSWORD_CHANGE_SELF')")
    public ResponseEntity<ApiResponse<Void>> changeMyPassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        userService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully. All active sessions revoked."));
    }
}

