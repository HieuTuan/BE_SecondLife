package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PostSubmitRequest(
    @NotBlank(message = "Title is required")
    @Size(max = 255)
    String title,
    @NotBlank(message = "Description is required")
    @Size(max = 10000)
    String description,
    @NotNull(message = "Price is required")
    @DecimalMin("1")
    @Digits(integer = 16, fraction = 2)
    BigDecimal price,
    @NotNull(message = "Shipping weight is required")
    @Min(value = 1, message = "Shipping weight must be at least 1 gram")
    Integer shippingWeight,
    @NotNull(message = "Shipping length is required")
    @Min(value = 1, message = "Shipping length must be at least 1 cm")
    Integer shippingLength,
    @NotNull(message = "Shipping width is required")
    @Min(value = 1, message = "Shipping width must be at least 1 cm")
    Integer shippingWidth,
    @NotNull(message = "Shipping height is required")
    @Min(value = 1, message = "Shipping height must be at least 1 cm")
    Integer shippingHeight
) {
}
