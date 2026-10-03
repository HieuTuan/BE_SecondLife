package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class OrderRequestDTO {
    @NotNull(message = "Post ID is required")
    private UUID postId;

    // Optional: Only if buying with a negotiated price
    private UUID negotiationId;
}
