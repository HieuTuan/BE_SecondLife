package com.secondlife.secondlife.dto.response;

import java.util.UUID;

public record CategoryQuestionTemplateResponse(
    UUID id,
    UUID categoryId,
    UUID itemId,
    String templateText
) {
}
