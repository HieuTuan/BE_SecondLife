package com.secondlife.secondlife.dto.commission;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import tools.jackson.databind.annotation.JsonDeserialize;

@ValidCommissionLimits
@Schema(description = "Default commission policy for all products", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record CommissionRuleRequest(
        @NotBlank(message = "name is required") @Size(max = 150, message = "name must not exceed 150 characters")
        @JsonDeserialize(using = CommissionJsonTypes.StringValue.class) @Schema(example = "Default commission") String name,
        @NotNull(message = "rate is required") @DecimalMin(value = "0", message = "rate must be between 0 and 1")
        @DecimalMax(value = "1", message = "rate must be between 0 and 1")
        @Digits(integer = 1, fraction = 6, message = "rate must have at most 6 decimal places")
        @JsonDeserialize(using = CommissionJsonTypes.NumberValue.class)
        @Schema(description = "Fraction: 0.05 means 5%", example = "0.05") BigDecimal rate,
        @NotNull(message = "minCommission is required") @DecimalMin(value = "0", message = "minCommission must not be negative")
        @Digits(integer = 16, fraction = 2, message = "minCommission allows at most 16 integer digits and 2 decimal places")
        @JsonDeserialize(using = CommissionJsonTypes.NumberValue.class)
        @Schema(description = "Minimum commission in VND", example = "10000") BigDecimal minCommission,
        @DecimalMin(value = "0", message = "maxCommission must not be negative")
        @Digits(integer = 16, fraction = 2, message = "maxCommission allows at most 16 integer digits and 2 decimal places")
        @JsonDeserialize(using = CommissionJsonTypes.NumberValue.class)
        @Schema(description = "Optional cap in VND, must be >= minCommission. Omit or use null for no cap; 0 means a zero cap", example = "100000") BigDecimal maxCommission,
        @NotNull(message = "active is required") @JsonDeserialize(using = CommissionJsonTypes.BooleanValue.class)
        @Schema(example = "true") Boolean active,
        @NotBlank(message = "reason is required") @Size(max = 1000, message = "reason must not exceed 1000 characters")
        @JsonDeserialize(using = CommissionJsonTypes.StringValue.class)
        @Schema(example = "Configure commission policy") String reason
) {
    @JsonAnySetter
    public void rejectUnsupportedField(String field, Object value) {
        throw new com.secondlife.secondlife.exception.InvalidRequestFieldException(field,
                "Unsupported field; only a default commission policy is supported");
    }
}
