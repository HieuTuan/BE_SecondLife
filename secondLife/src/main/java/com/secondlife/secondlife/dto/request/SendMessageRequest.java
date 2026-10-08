package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendMessageRequest {
    @NotBlank
    private String messageContent;
}
