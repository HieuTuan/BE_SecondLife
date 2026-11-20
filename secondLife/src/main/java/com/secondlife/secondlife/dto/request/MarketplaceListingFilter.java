package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record MarketplaceListingFilter(
        UUID categoryId,
        UUID itemId,
        @Size(max = 150) String q,
        @Size(max = 120) String brand,
        @Pattern(regexp = "NEW|LIKE_NEW|USED_GOOD|USED_FAIR|USED") String condition,
        @PositiveOrZero @Digits(integer = 16, fraction = 2) BigDecimal minPrice,
        @PositiveOrZero @Digits(integer = 16, fraction = 2) BigDecimal maxPrice,
        @Size(max = 120) String location,
        Boolean inspected
) {}
