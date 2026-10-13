package com.secondlife.secondlife.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
public record SellerPickupAddressRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "(?:0|\\+?84)[0-9]{9,10}",
                message = "Số điện thoại Việt Nam phải bắt đầu bằng 0, 84 hoặc +84 và có độ dài hợp lệ")
        @Schema(description = "Số điện thoại Việt Nam; chấp nhận dạng 0, 84 hoặc +84", example = "0912345678") String phone,
        @NotNull(message = "Vĩ độ địa chỉ lấy hàng là bắt buộc")
        @DecimalMin("-90") @DecimalMax("90") @Schema(description = "Vĩ độ địa chỉ lấy hàng", example = "10.7756") BigDecimal latitude,
        @NotNull(message = "Kinh độ địa chỉ lấy hàng là bắt buộc")
        @DecimalMin("-180") @DecimalMax("180") @Schema(description = "Kinh độ địa chỉ lấy hàng", example = "106.7019") BigDecimal longitude) {
    public SellerPickupAddressRequest(String name,String phone,String ignoredAddress,BigDecimal latitude,BigDecimal longitude) {
        this(name,phone,latitude,longitude);
    }
}
