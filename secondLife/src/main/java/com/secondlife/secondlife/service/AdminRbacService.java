package com.secondlife.secondlife.service;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.rbac.PermissionResponse;
import com.secondlife.secondlife.dto.rbac.CreatePermissionRequest;
import com.secondlife.secondlife.dto.rbac.UpdatePermissionRequest;
import com.secondlife.secondlife.dto.rbac.ReplaceRolePermissionsRequest;
import com.secondlife.secondlife.dto.rbac.RolePermissionAuditResponse;
import com.secondlife.secondlife.dto.rbac.RolePermissionsResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AdminRbacService {
    List<PermissionResponse> getPermissions();
    PermissionResponse getPermission(String permissionCode);
    PermissionResponse createPermission(CreatePermissionRequest request);
    PermissionResponse updatePermission(String permissionCode, UpdatePermissionRequest request);
    void deletePermission(String permissionCode);
    List<RolePermissionsResponse> getRoles();
    RolePermissionsResponse getRole(String roleCode);
    RolePermissionsResponse replaceRolePermissions(UUID adminId, String roleCode,
                                                   ReplaceRolePermissionsRequest request);
    PageResponse<RolePermissionAuditResponse> getRolePermissionAudit(String roleCode, Pageable pageable);
}
