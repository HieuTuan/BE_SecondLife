package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class NegotiationRequestDTO {
    @NotNull(message = "Post ID is required")
    private UUID postId;

    @NotNull(message = "Offered price is required")
    @Min(value = 1, message = "Offered price must be greater than 0")
    private BigDecimal offeredPrice;
}
