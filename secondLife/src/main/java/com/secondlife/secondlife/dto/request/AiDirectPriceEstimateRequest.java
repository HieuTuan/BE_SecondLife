package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiDirectPriceEstimateRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String title;

    private String category;
    private String brand;
    private String model;
    private String itemCondition;
    private String description;
    private List<String> imageUrls;
}
