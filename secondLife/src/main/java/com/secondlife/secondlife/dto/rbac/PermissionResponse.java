package com.secondlife.secondlife.dto.rbac;

import java.util.Set;

public record PermissionResponse(String code, String name, String description,
                                 Set<String> assignableRoles, boolean systemPermission) {
}
