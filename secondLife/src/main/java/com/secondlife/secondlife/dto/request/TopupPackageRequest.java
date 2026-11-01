package com.secondlife.secondlife.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@JsonIgnoreProperties("discountPercentage")
public record TopupPackageRequest(
        @NotBlank(message = "Tên gói không được để trống")
        @Size(max = 100, message = "Tên gói tối đa 100 ký tự")
        String name,
        @Size(max = 255, message = "Mô tả tối đa 255 ký tự")
        String description,
        @Min(value = 0, message = "Số lượt đăng tin không được âm")
        int postCredits,
        @Min(value = 0, message = "Số lượt Chat AI không được âm")
        int chatCredits,
        @Min(value = 0, message = "Số lượt định giá không được âm")
        int valuationCredits,
        @NotNull(message = "Giá gói không được để trống")
        @DecimalMin(value = "1000", message = "Giá gói tối thiểu là 1.000 VNĐ")
        @Digits(integer = 17, fraction = 2, message = "Giá gói tối đa 17 chữ số nguyên và 2 chữ số thập phân")
        BigDecimal price,
        @NotNull(message = "Trạng thái gói không được để trống")
        Boolean active
) {
}
