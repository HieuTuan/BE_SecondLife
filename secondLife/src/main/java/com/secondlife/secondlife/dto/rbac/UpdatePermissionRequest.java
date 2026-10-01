package com.secondlife.secondlife.dto.rbac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdatePermissionRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description,
        Set<@NotBlank String> assignableRoles) {
}
