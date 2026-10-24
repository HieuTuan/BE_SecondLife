package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AiDirectPriceEstimateRequest(
    @NotBlank(message = "Tên sản phẩm không được để trống")
    String title,
    String category,
    String brand,
    String model,
    String itemCondition,
    String description,
    List<String> imageUrls
) {
}
