package com.secondlife.secondlife.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistItemResultDto {
    private UUID itemId;
    private String itemTitle;
    private boolean passed;
    private String inspectorNote;
}
