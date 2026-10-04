package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.dto.shipping.ShippingAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Locale;

public record SellerOnboardingRequest(
        @NotBlank @Size(max = 30) String shopName,
        @NotNull @Valid ShippingAddress pickupAddress,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "\\+?[0-9]{9,15}") String phone) {
    public SellerOnboardingRequest {
        shopName = shopName == null ? null : shopName.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        phone = phone == null ? null : phone.strip();
    }
}
