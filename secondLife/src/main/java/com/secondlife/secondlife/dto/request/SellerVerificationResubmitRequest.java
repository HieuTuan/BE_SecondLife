package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SellerVerificationResubmitRequest(
        @NotBlank(message = "Document front URL is required")
        @Size(max = 1024, message = "Document front URL is too long")
        String documentFrontUrl,

        @NotBlank(message = "Document back URL is required")
        @Size(max = 1024, message = "Document back URL is too long")
        String documentBackUrl,

        @Size(max = 1024, message = "Selfie URL is too long")
        String selfieUrl
) {
    public SellerVerificationResubmitRequest(String documentFrontUrl, String documentBackUrl) {
        this(documentFrontUrl, documentBackUrl, null);
    }
}
