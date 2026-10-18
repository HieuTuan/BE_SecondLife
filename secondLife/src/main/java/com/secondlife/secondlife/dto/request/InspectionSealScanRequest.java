package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record InspectionSealScanRequest(
    @NotBlank(message = "Mã tem niêm phong không được để trống")
    String tamperSealId,
    @NotBlank(message = "Giai đoạn quét không được để trống")
    @Pattern(regexp = "^(INITIAL_SEAL|CARRIER_HANDOVER|DELIVERED_BUYER)$",
    message = "Giai đoạn quét phải là INITIAL_SEAL, CARRIER_HANDOVER hoặc DELIVERED_BUYER")
    String scanStage,
    @NotBlank(message = "Tình trạng tem không được để trống")
    @Pattern(regexp = "^(INTACT|TAMPERED)$", message = "Tình trạng tem phải là INTACT (còn nguyên) hoặc TAMPERED (bị rách/mở)")
    String sealCondition,
    String locationText,
    BigDecimal latitude,
    BigDecimal longitude,
    String note
) {
}
