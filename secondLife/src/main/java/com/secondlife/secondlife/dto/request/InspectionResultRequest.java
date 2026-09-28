package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InspectionResultRequest {

    @NotBlank(message = "Status is required (PASSED or FAILED)")
    private String status; // PASSED | FAILED

    private String note; // Ghi chú chi tiết kết quả kiểm định
}
