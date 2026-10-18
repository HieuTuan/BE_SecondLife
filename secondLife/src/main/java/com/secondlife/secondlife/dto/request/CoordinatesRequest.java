package com.secondlife.secondlife.dto.request;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
public record CoordinatesRequest(
    @NotNull @DecimalMin("-90") @DecimalMax("90") @Schema(example="21.0333") BigDecimal latitude,
    @NotNull @DecimalMin("-180") @DecimalMax("180") @Schema(example="105.7937") BigDecimal longitude) {}
