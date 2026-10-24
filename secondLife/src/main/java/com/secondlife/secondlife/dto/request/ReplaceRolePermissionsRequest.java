package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record ReplaceRolePermissionsRequest(
        @NotNull Set<String> expectedPermissionCodes,
        @NotNull Set<String> permissionCodes) {
}
