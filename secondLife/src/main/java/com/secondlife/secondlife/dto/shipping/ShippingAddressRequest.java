package com.secondlife.secondlife.dto.shipping;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
public record ShippingAddressRequest(
    @NotBlank @Size(max=100) String name,
    @NotBlank @Pattern(regexp="(?:0|\\+?84)[0-9]{9,10}",
        message="Số điện thoại Việt Nam phải bắt đầu bằng 0, 84 hoặc +84 và có độ dài hợp lệ")
    @Schema(description="Số điện thoại Việt Nam; chấp nhận dạng 0, 84 hoặc +84",example="0912345678") String phone,
    @NotNull @DecimalMin("-90") @DecimalMax("90") @Schema(description="Vĩ độ",example="21.0333") BigDecimal latitude,
    @NotNull @DecimalMin("-180") @DecimalMax("180") @Schema(description="Kinh độ",example="105.7937") BigDecimal longitude) {}
