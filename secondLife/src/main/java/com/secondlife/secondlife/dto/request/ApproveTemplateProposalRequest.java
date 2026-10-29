package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApproveTemplateProposalRequest(
        @NotBlank(message = "Nội dung kịch bản duyệt không được để trống")
        @Size(max = 10000, message = "Nội dung kịch bản không được vượt quá 10000 ký tự")
        String templateText
) {
}

