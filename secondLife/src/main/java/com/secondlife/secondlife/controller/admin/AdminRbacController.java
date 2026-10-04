package com.secondlife.secondlife.controller.admin;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.rbac.PermissionResponse;
import com.secondlife.secondlife.dto.rbac.CreatePermissionRequest;
import com.secondlife.secondlife.dto.rbac.UpdatePermissionRequest;
import com.secondlife.secondlife.dto.rbac.ReplaceRolePermissionsRequest;
import com.secondlife.secondlife.dto.rbac.RolePermissionAuditResponse;
import com.secondlife.secondlife.dto.rbac.RolePermissionsResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.AdminRbacService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.net.URI;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_RBAC_MANAGE')")
@Tag(name = "Admin - Permissions", description = "Permission catalog CRUD, role permission matrix and change history")
public class AdminRbacController {
    private final AdminRbacService rbacService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/permissions")
    @Operation(summary = "List permission catalog and assignable roles")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getPermissions() {
        return ResponseEntity.ok(ApiResponse.success(rbacService.getPermissions()));
    }

    @GetMapping("/permissions/{permissionCode}")
    @Operation(summary = "Get permission details and assignable roles")
    public ResponseEntity<ApiResponse<PermissionResponse>> getPermission(@PathVariable String permissionCode) {
        return ResponseEntity.ok(ApiResponse.success(rbacService.getPermission(permissionCode)));
    }

    @PostMapping("/permissions")
    @Operation(summary = "Initialize a missing permission defined and implemented by the backend")
    public ResponseEntity<ApiResponse<PermissionResponse>> createPermission(
            @Valid @RequestBody CreatePermissionRequest request) {
        PermissionResponse response = rbacService.createPermission(request);
        return ResponseEntity.created(URI.create("/api/admin/permissions/" + response.code()))
                .body(ApiResponse.success(response));
    }

    @PutMapping("/permissions/{permissionCode}")
    @Operation(summary = "Update permission metadata and custom permission's allowed roles")
    public ResponseEntity<ApiResponse<PermissionResponse>> updatePermission(
            @PathVariable String permissionCode,
            @Valid @RequestBody UpdatePermissionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(rbacService.updatePermission(permissionCode, request)));
    }

    @DeleteMapping("/permissions/{permissionCode}")
    @Operation(summary = "Delete an unassigned custom permission",
            description = "System permissions and permissions still assigned to a role cannot be deleted.")
    public ResponseEntity<ApiResponse<Void>> deletePermission(@PathVariable String permissionCode) {
        rbacService.deletePermission(permissionCode);
        return ResponseEntity.ok(ApiResponse.success("Permission deleted"));
    }

    @GetMapping("/roles")
    @Operation(summary = "List roles with their current permissions")
    public ResponseEntity<ApiResponse<List<RolePermissionsResponse>>> getRoles() {
        return ResponseEntity.ok(ApiResponse.success(rbacService.getRoles()));
    }

    @GetMapping("/roles/{roleCode}")
    @Operation(summary = "Get one role with its current permissions")
    public ResponseEntity<ApiResponse<RolePermissionsResponse>> getRole(@PathVariable String roleCode) {
        return ResponseEntity.ok(ApiResponse.success(rbacService.getRole(roleCode)));
    }

    @PutMapping("/roles/{roleCode}/permissions")
    @Operation(summary = "Replace a role's permissions using its last loaded permission set")
    public ResponseEntity<ApiResponse<RolePermissionsResponse>> replaceRolePermissions(
            @AuthenticationPrincipal CustomUserDetails admin,
            @PathVariable String roleCode,
            @Valid @RequestBody ReplaceRolePermissionsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(rbacService.replaceRolePermissions(
                currentUserProvider.resolveAdminId(admin), roleCode, request)));
    }

    @GetMapping("/roles/{roleCode}/permission-changes")
    @Operation(summary = "Get a role's permission change history")
    public ResponseEntity<ApiResponse<PageResponse<RolePermissionAuditResponse>>> getRolePermissionAudit(
            @PathVariable String roleCode,
            @ParameterObject @SortDefault(sort = "changedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(rbacService.getRolePermissionAudit(roleCode, pageable)));
    }
}
