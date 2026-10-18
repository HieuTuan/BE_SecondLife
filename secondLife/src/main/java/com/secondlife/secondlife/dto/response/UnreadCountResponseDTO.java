package com.secondlife.secondlife.dto.response;

import lombok.Builder;

@Builder
public record UnreadCountResponseDTO(
    long unreadCount
) {
}
