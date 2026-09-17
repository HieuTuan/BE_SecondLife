package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    String email,

    @NotBlank(message = "Password is required")
    @StrongPassword
    String password,

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    String fullName,

    @Pattern(regexp = "^[+0-9\\-\\s()]{7,20}$", message = "Phone number is invalid")
    String phone
) {}
