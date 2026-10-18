package com.secondlife.secondlife.dto.request;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
public record CreateShipmentRequest(
    @NotNull UUID requestId,
    @NotBlank @Pattern(regexp="SELLER_TO_BUYER|SELLER_TO_CENTER|CENTER_TO_BUYER|BUYER_TO_SELLER") String leg,
    @Valid @Schema(description="Normally omit. CENTER_TO_BUYER uses the saved center in the paid quote; if supplied, contacts and coordinates must match") ShippingAddressRequest fromAddress,
    @Valid @Schema(description="Required for SELLER_TO_CENTER. Backend resolves address text from coordinates") ShippingAddressRequest toAddress,
    @Size(max=2000) @Schema(description="Required decision reference/reason for a return approved by STAFF/ADMIN") String reason) {}
