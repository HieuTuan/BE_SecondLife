package com.secondlife.secondlife.dto.response;

import java.util.UUID;

public record ItemResponse(UUID id, String name, UUID categoryId, CategoryResponse category,
                           boolean selectable, UUID replacementForItemId, String variantCode) {
    public ItemResponse(UUID id, String name, UUID categoryId, CategoryResponse category) {
        this(id, name, categoryId, category, true, null, null);
    }
}
