package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record InspectionResultRequest(
    @NotBlank(message = "Status is required (PASSED or FAILED)")
    @Pattern(regexp = "^(PASSED|FAILED)$", message = "Status must be PASSED or FAILED")
    String status, // PASSED | FAILED
    String note, // Ghi chú chi tiết kết quả kiểm định
    String videoProofUrl, // Video quá trình kiểm tra máy móc
    String videoProofPublicId, // Cloudinary publicId của video kiểm định
    String unboxingVideoUrl, // Video khui kiện hàng (nếu gói AT_CENTER)
    String unboxingVideoPublicId, // Cloudinary publicId của video mở hộp
    Integer conditionScore,
    String conditionGrade,
    String tamperSealId,
    String barcode,
    List<ChecklistItemResultDto> checklistResults
) {
}
