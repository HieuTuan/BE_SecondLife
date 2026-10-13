package com.secondlife.secondlife.dto.shipping;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
public record ReconcileShipmentRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,50}") String orderCode) {}
