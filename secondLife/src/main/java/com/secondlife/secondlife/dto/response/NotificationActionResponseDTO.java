package com.secondlife.secondlife.dto.response;

import lombok.Builder;

@Builder
public record NotificationActionResponseDTO(
    boolean success,
    int affectedCount,
    String message
) {
}
