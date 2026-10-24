package com.secondlife.secondlife.dto.request;

import java.util.UUID;

public record ChecklistItemResultDto(
    UUID itemId,
    String itemTitle,
    boolean passed,
    String inspectorNote
) {
}
