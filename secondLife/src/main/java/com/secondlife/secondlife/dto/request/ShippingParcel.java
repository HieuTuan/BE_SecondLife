package com.secondlife.secondlife.dto.request;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.media.Schema;
public record ShippingParcel(
    @Min(1) @Schema(description="Packed weight in grams; transport capacity is checked against Lalamove vehicles when quoting") int weight,
    @Min(1) @Schema(description="Packed length in centimeters; checked against Lalamove vehicle dimensions when quoting") int length,
    @Min(1) @Schema(description="Packed width in centimeters; checked against Lalamove vehicle dimensions when quoting") int width,
    @Min(1) @Schema(description="Packed height in centimeters; package remains upright when checking Lalamove vehicle dimensions") int height) {}
