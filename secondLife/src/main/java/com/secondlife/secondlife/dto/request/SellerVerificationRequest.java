package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.enums.VerificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SellerVerificationRequest(
        @NotNull(message = "Verification type is required")
        VerificationType verificationType,

        @NotBlank(message = "Document number is required")
        @Size(max = 100, message = "Document number is too long")
        String documentNumber,

        @NotBlank(message = "Document front URL is required")
        @Size(max = 1024, message = "Document front URL is too long")
        String documentFrontUrl,

        @NotBlank(message = "Document back URL is required")
        @Size(max = 1024, message = "Document back URL is too long")
        String documentBackUrl,

        @Size(max = 1024, message = "Selfie URL is too long")
        String selfieUrl
) {
    public SellerVerificationRequest(VerificationType verificationType, String documentNumber,
                                     String documentFrontUrl, String documentBackUrl) {
        this(verificationType, documentNumber, documentFrontUrl, documentBackUrl, null);
    }
}
