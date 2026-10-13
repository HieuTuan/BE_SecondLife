package com.secondlife.secondlife.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostFinalizeResponse {
    private String title;
    private String description;
    private BigDecimal suggestedPrice;

    public PostFinalizeResponse(String description, BigDecimal suggestedPrice) {
        this.title = null;
        this.description = description;
        this.suggestedPrice = suggestedPrice;
    }
}

