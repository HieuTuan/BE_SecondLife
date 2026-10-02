package com.secondlife.secondlife.dto.rbac;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record ReplaceUserRolesRequest(
        @NotNull @Size(max = 6) Set<@NotNull @Pattern(regexp = "[A-Z][A-Z_]{1,49}") String> expectedRoleCodes,
        @NotNull @Size(max = 6) Set<@NotNull @Pattern(regexp = "[A-Z][A-Z_]{1,49}") String> roleCodes
) { }
