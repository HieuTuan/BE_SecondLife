package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.TemplateProposalStatus;

import java.time.Instant;
import java.util.UUID;

public record CategoryQuestionTemplateProposalResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        UUID itemId,
        String itemName,
        String templateText,
        TemplateProposalStatus status,
        int requestCount,
        UUID triggeredBy,
        UUID reviewedBy,
        Instant reviewedAt,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt
) {
}

