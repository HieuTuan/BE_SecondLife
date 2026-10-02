package com.secondlife.secondlife.dto.rbac;

import java.util.Set;
import java.util.UUID;

public record UserRolesResponse(UUID userId, Set<String> roleCodes, Set<String> permissionCodes) { }
