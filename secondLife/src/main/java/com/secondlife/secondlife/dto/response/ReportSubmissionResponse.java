package com.secondlife.secondlife.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReportSubmissionResponse(
        UUID id,
        UUID reportId,
        UUID authorId,
        String authorRole,
        UUID requestId,
        String description,
        List<String> evidenceUrls,
        Instant createdAt
) {
}
