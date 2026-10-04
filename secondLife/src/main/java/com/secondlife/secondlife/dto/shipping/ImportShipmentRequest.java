package com.secondlife.secondlife.dto.shipping;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record ImportShipmentRequest(
        @NotNull UUID requestId,
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,50}") String orderCode,
        @NotBlank @Size(max=2000) String reason) {}
