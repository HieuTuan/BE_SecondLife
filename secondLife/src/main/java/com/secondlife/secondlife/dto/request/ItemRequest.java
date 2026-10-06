package com.secondlife.secondlife.dto.request;

import lombok.Data;
import java.util.UUID;

@Data
public class ItemRequest {
    private String name;
    private UUID categoryId;
}
