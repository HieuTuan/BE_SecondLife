package com.secondlife.secondlife.dto.shipping;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;

public record ShippingAddress(
        @NotBlank @Size(max=100) String name,
        @NotBlank @Pattern(regexp="\\+?[0-9]{9,15}") String phone,
        @NotBlank @Size(max=500) String address,
        @NotBlank @Size(max=100) String provinceName,
        @Size(max=100) String districtName,
        @NotBlank @Size(max=100) String wardName,
        @Positive @Schema(description="Legacy GHN district ID; required only for legacy addresses") Integer districtId,
        @Size(max=30) @Schema(description="Legacy GHN ward code; required only for legacy addresses") String wardCode,
        boolean newAddress) {
    public ShippingAddress {
        if (newAddress) { districtName=null; districtId=null; wardCode=null; }
    }
    @AssertTrue(message="districtName is required for the legacy address format")
    public boolean isDistrictNameValid() { return newAddress || (districtName != null && !districtName.isBlank()); }
    @AssertTrue(message="districtId and wardCode are required for legacy addresses")
    public boolean isLegacyCodesValid() { return newAddress || (districtId!=null && wardCode!=null && !wardCode.isBlank()); }
}
