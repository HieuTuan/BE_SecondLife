package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PostSubmitRequest {
    @NotBlank(message = "Title is required")
    @jakarta.validation.constraints.Size(max = 255)
    private String title;
    
    @NotBlank(message = "Description is required")
    @jakarta.validation.constraints.Size(max = 10000)
    private String description;
    
    @NotNull(message = "Price is required")
    @jakarta.validation.constraints.DecimalMin("1")
    @jakarta.validation.constraints.Digits(integer = 16, fraction = 2)
    private BigDecimal price;
}
