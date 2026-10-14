package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InspectionSealScanRequest {

    @NotBlank(message = "Mã tem niêm phong không được để trống")
    private String tamperSealId;

    @NotBlank(message = "Giai đoạn quét không được để trống")
    @Pattern(regexp = "^(INITIAL_SEAL|CARRIER_HANDOVER|DELIVERED_BUYER)$",
             message = "Giai đoạn quét phải là INITIAL_SEAL, CARRIER_HANDOVER hoặc DELIVERED_BUYER")
    private String scanStage;

    @NotBlank(message = "Tình trạng tem không được để trống")
    @Pattern(regexp = "^(INTACT|TAMPERED)$", message = "Tình trạng tem phải là INTACT (còn nguyên) hoặc TAMPERED (bị rách/mở)")
    private String sealCondition;

    private String locationText;
    private String note;
}
