package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    String fullName,

    @Pattern(regexp = "^[+0-9\\-\\s()]{7,20}$", message = "Phone number is invalid")
    String phone,

    @Size(max = 1024, message = "Avatar URL is too long")
    String avatarUrl
) {}
