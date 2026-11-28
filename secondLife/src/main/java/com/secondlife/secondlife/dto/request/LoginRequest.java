package com.secondlife.secondlife.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.secondlife.secondlife.common.StrictStringDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.Locale;
import java.util.UUID;

public record LoginRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    @JsonDeserialize(using = StrictStringDeserializer.class)
    String email,

    @NotBlank(message = "Password is required")
    @Size(max = 72, message = "Password must not exceed 72 characters")
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
    String password,

    @Schema(description = "Explicit user confirmation of current Terms of Service & Operating Policy", example = "true")
    Boolean acceptTerms,

    @Schema(description = "ID of the active Terms of Service policy version accepted by the user")
    UUID acceptedPolicyVersionId,

    @Schema(description = "Client source: WEB, MOBILE_BUYER, MOBILE_SELLER, MOBILE_INSPECTOR, MOBILE, API", example = "WEB")
    String acceptanceSource
) {
    public LoginRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        // Passwords are compared exactly as supplied; never trim or normalize them.
    }

    public LoginRequest(String email, String password) {
        this(email, password, null, null, null);
    }

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=[REDACTED]]";
    }
}
