package com.secondlife.secondlife.dto.rbac;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record ReplaceRolePermissionsRequest(
        @NotNull Set<String> expectedPermissionCodes,
        @NotNull Set<String> permissionCodes) {
}
