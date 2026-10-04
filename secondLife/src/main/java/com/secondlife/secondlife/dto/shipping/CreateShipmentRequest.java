package com.secondlife.secondlife.dto.shipping;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
public record CreateShipmentRequest(
    @NotNull UUID requestId,
    @NotBlank @Pattern(regexp="SELLER_TO_BUYER|SELLER_TO_CENTER|CENTER_TO_BUYER|BUYER_TO_SELLER") String leg,
    @Valid @Schema(description="Normally omit. CENTER_TO_BUYER uses the saved center in the paid quote; if supplied, it must match") ShippingAddress fromAddress,
    @Valid @Schema(description="Required for SELLER_TO_CENTER. Other legs use stored order addresses") ShippingAddress toAddress,
    @Size(max=2000) @Schema(description="Required decision reference/reason for a return approved by STAFF/ADMIN") String reason) {}
