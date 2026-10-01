package com.secondlife.secondlife.service;

import com.secondlife.secondlife.enums.RoleCode;

import java.util.UUID;

/** Internal role assignment boundary. No client-facing role mutation endpoint exists. */
public interface RoleAssignmentService {
    void grantRole(UUID userId, RoleCode roleCode);

    void removeRole(UUID userId, RoleCode roleCode);
}
