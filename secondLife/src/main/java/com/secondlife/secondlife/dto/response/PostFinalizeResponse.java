package com.secondlife.secondlife.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class PostFinalizeResponse {
    private String description;
    private BigDecimal suggestedPrice;
}
