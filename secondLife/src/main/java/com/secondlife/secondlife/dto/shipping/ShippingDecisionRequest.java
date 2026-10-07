package com.secondlife.secondlife.dto.shipping;
import jakarta.validation.constraints.*;
public record ShippingDecisionRequest(@NotBlank @Size(max=2000) String reason) {}
