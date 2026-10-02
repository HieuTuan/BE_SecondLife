package com.secondlife.secondlife.dto.rbac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreatePermissionRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description,
        @NotNull Set<@NotBlank String> assignableRoles) {
}
