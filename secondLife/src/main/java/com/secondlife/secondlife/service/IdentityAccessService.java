package com.secondlife.secondlife.service;

import java.util.Set;
import java.util.UUID;

public interface IdentityAccessService {
    boolean hasAnyRole(UUID userId, Set<String> roleCodes);
}
