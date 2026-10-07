package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.entity.Category;
import java.util.UUID;

public record CategoryResponse(UUID id, String name, String description) {
    public static CategoryResponse from(Category c) { return new CategoryResponse(c.getId(), c.getName(), c.getDescription()); }
}
