package com.secondlife.secondlife.dto.response;

import java.util.UUID;

public record ResolvedQuestionTemplateResponse(
        UUID officialTemplateId,
        String templateText,
        boolean temporaryGenerated
) {
}

