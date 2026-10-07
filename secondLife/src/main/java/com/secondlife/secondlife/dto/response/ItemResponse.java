package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.entity.Item;
import java.util.UUID;

public record ItemResponse(UUID id, String name, UUID categoryId, CategoryResponse category) {
    public static ItemResponse from(Item i) {
        return new ItemResponse(i.getId(), i.getName(), i.getCategory().getId(), CategoryResponse.from(i.getCategory()));
    }
}
