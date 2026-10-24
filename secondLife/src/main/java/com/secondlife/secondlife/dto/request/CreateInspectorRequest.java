package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateInspectorRequest(
    @NotBlank
    @Email
    String email,
    @NotBlank
    String fullName,
    @NotBlank
    String password
) {
}
