package com.secondlife.secondlife.dto.reputation;
import jakarta.validation.constraints.*;
public record CancelOrderRequest(@NotNull ReasonCode reasonCode,@NotBlank @Size(max=2000) String reason) {
    public enum ReasonCode { BUYER_REQUESTED, SELLER_OUT_OF_STOCK, SELLER_CANNOT_FULFILL }
}
