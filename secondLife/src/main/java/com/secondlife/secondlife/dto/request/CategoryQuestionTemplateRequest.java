package com.secondlife.secondlife.dto.request;

import java.util.UUID;

public record CategoryQuestionTemplateRequest(
    UUID categoryId,
    UUID itemId,
    String templateText
) {
}
