package com.secondlife.secondlife.dto.rbac;

import java.util.Set;

public record RolePermissionsResponse(String code, String name, String description,
                                      boolean editable, Set<String> permissionCodes) {
}
