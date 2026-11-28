package com.secondlife.secondlife.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record SellerOnboardingRequest(
        @NotBlank(message = "Vui lòng nhập tên gian hàng / cửa hàng.")
        @Size(min = 5, max = 30, message = "Tên gian hàng phải từ 5 đến 30 ký tự.")
        @Pattern(
                regexp = "^[\\p{L}\\p{N} ]+$",
                message = "Tên gian hàng không được chứa ký tự đặc biệt."
        )
        String shopName,
        @Valid SellerPickupAddressRequest pickupAddress,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "\\+?[0-9]{9,15}") String phone) {
    public SellerOnboardingRequest {
        shopName = shopName == null ? null : shopName.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        phone = phone == null ? null : phone.strip();
    }
}
