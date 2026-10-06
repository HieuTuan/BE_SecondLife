package com.secondlife.secondlife.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record SellerPickupAddressRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "\\+?[0-9]{9,15}") String phone,
        @NotBlank @Size(max = 500) String address,
        @NotNull @Positive @Schema(description = "GHN province _id from GET /api/v1/shipping/provinces") Integer provinceId,
        @NotNull @Positive @Schema(description = "GHN ward _id from GET /api/v1/shipping/wards?provinceId=...") Integer wardId) {}
