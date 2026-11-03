package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.Size;

public record RejectTemplateProposalRequest(
        @Size(max = 1000, message = "Lý do từ chối không được vượt quá 1000 ký tự")
        String reason
) {
}

