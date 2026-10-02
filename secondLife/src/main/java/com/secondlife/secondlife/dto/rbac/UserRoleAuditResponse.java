package com.secondlife.secondlife.dto.rbac;

import java.time.Instant;
import java.util.UUID;

public record UserRoleAuditResponse(UUID id, String roleCode, String action, UUID changedBy, Instant changedAt) { }
