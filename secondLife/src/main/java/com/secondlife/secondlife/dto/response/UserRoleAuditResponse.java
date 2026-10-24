package com.secondlife.secondlife.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UserRoleAuditResponse(UUID id, String roleCode, String action, UUID changedBy, Instant changedAt) { }
