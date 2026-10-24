package com.secondlife.secondlife.dto.response;

import java.time.Instant;
import java.util.UUID;

public record RolePermissionAuditResponse(UUID id, String permissionCode, String action,
                                          UUID changedBy, Instant changedAt) {
}
