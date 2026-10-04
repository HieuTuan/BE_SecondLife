package com.secondlife.secondlife.dto.shipping;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
public record ShippingParcel(
    @Min(1) @Max(50000) @Schema(description="Packed weight in grams; GHN maximum 50 kg") int weight,
    @Min(1) @Max(200) @Schema(description="Packed length in centimeters") int length,
    @Min(1) @Max(200) int width,
    @Min(1) @Max(200) int height) {}
