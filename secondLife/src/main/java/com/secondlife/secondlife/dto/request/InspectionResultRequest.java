package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InspectionResultRequest {

    @NotBlank(message = "Status is required (PASSED or FAILED)")
    @Pattern(regexp = "^(PASSED|FAILED)$", message = "Status must be PASSED or FAILED")
    private String status; // PASSED | FAILED

    private String note; // Ghi chú chi tiết kết quả kiểm định

    private String videoProofUrl; // Video quá trình kiểm tra máy móc
    private String unboxingVideoUrl; // Video khui kiện hàng (nếu gói AT_CENTER)

    private List<ChecklistItemResultDto> checklistResults;
}
