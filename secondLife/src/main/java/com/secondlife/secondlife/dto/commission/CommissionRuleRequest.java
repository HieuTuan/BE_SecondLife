package com.secondlife.secondlife.dto.commission;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Schema(description = "Default commission policy for all products", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record CommissionRuleRequest(
        @NotBlank @Size(max = 150) @Schema(example = "Default commission") String name,
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 6)
        @Schema(description = "Fraction: 0.05 means 5%", example = "0.05") BigDecimal rate,
        @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) @Schema(example = "0") BigDecimal minCommission,
        @DecimalMin("0") @Digits(integer = 16, fraction = 2)
        @Schema(description = "Optional cap; null means no cap") BigDecimal maxCommission,
        @NotNull @Schema(example = "true") Boolean active,
        @NotBlank @Size(max = 1000) @Schema(example = "Configure commission policy") String reason
) {
    @JsonAnySetter
    public void rejectUnsupportedField(String field, Object value) {
        throw new IllegalArgumentException("Unsupported commission field: " + field + "; only a default policy is supported");
    }
}
