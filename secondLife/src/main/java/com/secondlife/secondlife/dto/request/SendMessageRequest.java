package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(
    @NotBlank
    String messageContent
) {
}
