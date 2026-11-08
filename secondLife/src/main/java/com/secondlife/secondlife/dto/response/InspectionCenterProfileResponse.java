package com.secondlife.secondlife.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InspectionCenterProfileResponse(
        UUID id,
        String name,
        String phone,
        String province,
        String district,
        String ward,
        String streetAddress,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        @Schema(description = "Đủ thông tin và GPS trong vùng cấu hình; không bảo đảm hãng hỗ trợ mọi tuyến hoặc đã có báo giá")
        boolean shippingAddressConfigured,
        Instant updatedAt
) {
}
