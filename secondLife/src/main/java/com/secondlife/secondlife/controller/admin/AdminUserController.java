package com.secondlife.secondlife.controller.admin;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.AdminStatusUpdateRequest;
import com.secondlife.secondlife.dto.response.UserAdminResponse;
import com.secondlife.secondlife.dto.rbac.*;
import com.secondlife.secondlife.service.AdminUserRoleService;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin - Users", description = "Admin user administration and status management APIs")
public class AdminUserController {

    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;
    private final AdminUserRoleService adminUserRoleService;

    @GetMapping("/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_RBAC_MANAGE')")
    @Operation(summary = "Read assigned roles and effective permissions of a user")
    public ResponseEntity<ApiResponse<UserRolesResponse>> getUserRoles(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success("Get user roles successfully", adminUserRoleService.getUserRoles(userId)));
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_RBAC_MANAGE')")
    @Operation(summary = "Replace user roles; changed users must sign in again")
    public ResponseEntity<ApiResponse<UserRolesResponse>> replaceUserRoles(
            @AuthenticationPrincipal CustomUserDetails admin, @PathVariable UUID userId,
            @Valid @RequestBody ReplaceUserRolesRequest request) {
        return ResponseEntity.ok(ApiResponse.success("User roles updated successfully",
                adminUserRoleService.replaceUserRoles(currentUserProvider.resolveAdminId(admin), userId, request)));
    }

    @GetMapping("/{userId}/role-changes")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_RBAC_MANAGE')")
    @Operation(summary = "Read user role assignment audit history")
    public ResponseEntity<ApiResponse<PageResponse<UserRoleAuditResponse>>> getUserRoleAudit(
            @PathVariable UUID userId,
            @org.springdoc.core.annotations.ParameterObject @SortDefault(sort = "changedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Get user role audit successfully", adminUserRoleService.getUserRoleAudit(userId, pageable)));
    }

    @GetMapping
    @Operation(summary = "Get users with filtering and pagination")
    @PreAuthorize("hasAuthority('USER_READ_ANY')")
    public ResponseEntity<ApiResponse<PageResponse<UserAdminResponse>>> getUsers(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) String role,
            @org.springdoc.core.annotations.ParameterObject @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<UserAdminResponse> response = userService.getAdminUsers(email, status, role, pageable);
        return ResponseEntity.ok(ApiResponse.success("Get users successfully", response));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user details by ID")
    @PreAuthorize("hasAuthority('USER_READ_ANY')")
    public ResponseEntity<ApiResponse<UserAdminResponse>> getUserById(@PathVariable UUID userId) {
        UserAdminResponse response = userService.getAdminUserById(userId);
        return ResponseEntity.ok(ApiResponse.success("Get user details successfully", response));
    }

    @PatchMapping("/{userId}/status")
    @Operation(summary = "Update user account status (ACTIVE, LOCKED, DISABLED)")
    @PreAuthorize("hasAuthority('USER_STATUS_UPDATE')")
    public ResponseEntity<ApiResponse<UserAdminResponse>> updateUserStatus(
            @AuthenticationPrincipal CustomUserDetails currentAdmin,
            @PathVariable UUID userId,
            @Valid @RequestBody AdminStatusUpdateRequest request
    ) {
        UUID adminId = currentUserProvider.resolveAdminId(currentAdmin);
        UserAdminResponse response = userService.updateAdminUserStatus(adminId, userId, request);
        return ResponseEntity.ok(ApiResponse.success("User status updated successfully", response));
    }
}

