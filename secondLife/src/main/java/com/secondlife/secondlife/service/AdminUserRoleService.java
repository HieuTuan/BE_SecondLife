package com.secondlife.secondlife.service;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.rbac.*;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface AdminUserRoleService {
    UserRolesResponse getUserRoles(UUID userId);
    UserRolesResponse replaceUserRoles(UUID adminId, UUID userId, ReplaceUserRolesRequest request);
    PageResponse<UserRoleAuditResponse> getUserRoleAudit(UUID userId, Pageable pageable);
}
