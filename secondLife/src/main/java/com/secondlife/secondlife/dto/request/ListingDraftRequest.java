package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** PUT replaces the editable product details; uploaded image/category remain attached. */
public record ListingDraftRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 10000) String description,
        @Size(max = 50) String itemCondition,
        @DecimalMin("1") @Digits(integer = 16, fraction = 2) BigDecimal price) {}
