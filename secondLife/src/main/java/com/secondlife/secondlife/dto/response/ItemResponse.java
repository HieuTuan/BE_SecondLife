package com.secondlife.secondlife.dto.response;

import java.util.UUID;

public record ItemResponse(UUID id, String name, UUID categoryId, CategoryResponse category) {

}
