package com.secondlife.secondlife.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record InspectionCenterProfileRequest(
        @NotBlank @Size(min = 2, max = 100) String name,
        @NotBlank @Pattern(regexp = "(?:0|\\+?84)[0-9]{9,10}",
                message = "Hotline phải là số điện thoại Việt Nam hợp lệ, không chứa dấu cách") String phone,
        @NotBlank @Size(max = 100) String province,
        @Size(max = 100) @Schema(description = "Quận/huyện nếu còn được sử dụng; có thể bỏ trống") String district,
        @NotBlank @Size(max = 100) String ward,
        @NotBlank @Size(max = 255) @Schema(description = "Số nhà, đường, tòa nhà; không lặp lại phường và tỉnh/thành") String streetAddress,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude
) {
}
