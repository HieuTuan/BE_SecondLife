package com.secondlife.secondlife.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record NotificationResponseDTO(
    UUID id,
    String title,
    String message,
    String type,
    UUID referenceId,
    String referenceType,
    String actionUrl,
    @JsonProperty("read")
    boolean isRead,
    Instant readAt,
    Instant createdAt
) {
}
