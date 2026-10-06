package com.secondlife.secondlife.dto.request;

import lombok.Data;
import java.util.UUID;

@Data
public class CategoryQuestionTemplateRequest {
    private UUID categoryId;
    private UUID itemId;
    private String templateText;
}
