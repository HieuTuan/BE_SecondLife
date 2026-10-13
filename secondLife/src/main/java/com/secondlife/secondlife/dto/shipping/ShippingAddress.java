package com.secondlife.secondlife.dto.shipping;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
public record ShippingAddress(
    @NotBlank @Size(max=100) String name,
    @NotBlank @Pattern(regexp="\\+?[0-9]{9,15}") String phone,
    @NotBlank @Size(max=500) String address,
    @NotNull(message="Vĩ độ địa chỉ là bắt buộc") @DecimalMin("-90") @DecimalMax("90")
    @Schema(description="Vĩ độ địa chỉ",example="10.7756") BigDecimal latitude,
    @NotNull(message="Kinh độ địa chỉ là bắt buộc") @DecimalMin("-180") @DecimalMax("180")
    @Schema(description="Kinh độ địa chỉ",example="106.7019") BigDecimal longitude) {
    public ShippingAddress {
        if(latitude!=null) latitude=latitude.stripTrailingZeros();
        if(longitude!=null) longitude=longitude.stripTrailingZeros();
    }
}
